#!/usr/bin/env python3
"""把已构建的中文电子书导出为分册 PDF（Edge headless 打印 + pypdf 合并）。

用法（先构建，或用 --build 自动构建）：
    python scripts/export-pdf.py [--build] [--skip-print]

输出到 dist-pdf/：
    架构炼金术-合集.pdf            全书
    架构炼金术-01-架构模式.pdf     第一部分 + 第二部分
    架构炼金术-02-决策与案例.pdf   第三部分 + 第四部分
    架构炼金术-03-从零构建.pdf     第五部分
    架构炼金术-04-接手工程.pdf     第六部分
    架构炼金术-05-治理与运维.pdf   第七部分 + 第八部分
    架构炼金术-06-实操指南.pdf     实操指南
    架构炼金术-07-AI系统.pdf       第九部分（AI 系统）

打印结果缓存于 dist-pdf/.pages/：分册方案与站点内容均未变时无需重新打印；
--skip-print 强制使用缓存（缓存缺失或已过期会报错）。

每册含：封面（作者/版本/日期）→ 目录（带页码）→ 正文；
逐页底部盖页码（除封面），书签两级（分组 → 逐篇），并写入 PDF 元数据。

依赖 pypdf：pip install -r scripts/requirements.txt
页码盖章另需 pymupdf（缺失时跳过页码，其余功能不受影响）。
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / ".vitepress" / "dist"
OUT_DIR = ROOT / "dist-pdf"
CACHE_DIR = OUT_DIR / ".pages"
MANIFEST = CACHE_DIR / "manifest.json"
CONFIG = ROOT / ".vitepress" / "config.mts"
BASE = "/distribution-architecture/"
PORT = 4173
PREVIEW_URL = f"http://127.0.0.1:{PORT}{BASE}"

EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]

SHELL_MARKERS = ("Skip to content", "Main Navigation")

# (文件名, 封面主标题, 封面副标题, 分组标题列表；None = 全部)
# 副标题中 {n} 在运行时替换为正文页数
OUTPUTS: list[tuple[str, str, str, list[str] | None]] = [
    ("架构炼金术-合集", "架构炼金术", "全书合集 · {n} 篇", None),
    ("架构炼金术-01-架构模式", "架构模式", "基础模式 + 进阶模式",
     ["第一部分：基础模式", "第二部分：进阶模式"]),
    ("架构炼金术-02-决策与案例", "决策与案例", "技术决策 ADR + 生产验证案例",
     ["第三部分：技术决策", "第四部分：实战案例"]),
    ("架构炼金术-03-从零构建", "从零构建", "七步教程：领域模型到合规检查",
     ["第五部分：从零构建"]),
    ("架构炼金术-04-接手工程", "接手工程", "21 问 + 10 模板 + 虚构案例串跑",
     ["第六部分：交付方法论"]),
    ("架构炼金术-05-治理与运维", "治理与运维", "组合治理 + 架构评审 + 生产运维",
     ["第七部分：架构治理与工程规范", "第八部分：生产运维"]),
    ("架构炼金术-06-实操指南", "实操指南", "快速开始 / 扩展模块 / 定制权限 / Boot3 迁移",
     ["实操指南"]),
    ("架构炼金术-07-AI系统", "AI 系统", "组件正确 ≠ 系统有效 + 声明级校验门",
     ["第九部分：AI 系统"]),
]


def find_edge() -> str:
    for p in EDGE_CANDIDATES:
        if Path(p).exists():
            return p
    for name in ("msedge", "msedge.exe"):
        found = shutil.which(name)
        if found:
            return found
    raise FileNotFoundError("未找到 Microsoft Edge，无法打印 PDF")


def parse_sidebar() -> list[tuple[str, list[str]]]:
    """从 config.mts 解析中文侧边栏的分组顺序。

    只认「独立成行且下一行是 items: 的 text:」作为分组标题，
    排除英文侧边栏（链接均含 /en/）、导航首页与 editLink。
    解析完校验：非 /en/ 的 link 总数必须等于解析出的 link 总数（首页除外），
    否则说明有链接未进入任何分组（正则漏配等），直接报错并列出缺失行号。
    """
    lines = CONFIG.read_text(encoding="utf-8").splitlines()
    groups: list[tuple[str, list[str]]] = []
    seen_lines: list[int] = []      # 所有非 /en/ 的 link 行（1-based）
    parsed_lines: list[int] = []    # 实际进入分组的 link 行
    home_lines: list[int] = []      # 首页导航（允许不入书）
    for i, line in enumerate(lines):
        if "/en/" in line or "http" in line:
            continue
        m_group = re.search(r"""text:\s*['"]([^'"]+)['"]\s*,\s*$""", line)
        if m_group:
            nxt = lines[i + 1] if i + 1 < len(lines) else ""
            if "items" in nxt:
                groups.append((m_group.group(1), []))
            continue
        m_link = re.search(r"""link:\s*['"](/[^'"]*)['"]""", line)
        if m_link:
            seen_lines.append(i + 1)
            link = m_link.group(1)
            if link == "/":  # 首页导航不进书，封面替代
                home_lines.append(i + 1)
            elif groups:
                groups[-1][1].append(link)
                parsed_lines.append(i + 1)
    missing = sorted(set(seen_lines) - set(parsed_lines) - set(home_lines))
    if missing:
        detail = "\n".join(f"  L{n}: {lines[n - 1].strip()}" for n in missing)
        raise SystemExit(
            f"config.mts 解析出 {len(parsed_lines)} 条正文链接，"
            f"但共有 {len(seen_lines) - len(home_lines)} 条非 /en/ link，"
            f"以下行未进入任何分组：\n{detail}"
        )
    return [(t, links) for t, links in groups if links]


def expand_case_docs(link: str) -> list[str]:
    """案例 README 后追加其 docs/ 下的演进子文档（若存在）。"""
    if not re.match(r"^/case-studies/[^/]+/$", link):
        return [link]
    rel = link.strip("/")
    docs_dir = DIST / rel / "docs"
    if not docs_dir.is_dir():
        return [link]
    subs = sorted(p for p in docs_dir.glob("*.html") if p.name != "index.html")
    return [link] + [f"{rel}/docs/{p.stem}" for p in subs]


def book_groups() -> list[tuple[str, list[str]]]:
    """前言置首 + 案例子文档展开。"""
    groups: list[tuple[str, list[str]]] = []
    if (DIST / "preface.html").exists():
        groups.append(("前言", ["/preface"]))
    for title, links in parse_sidebar():
        groups.append((title, [p for link in links for p in expand_case_docs(link)]))
    return groups


def url_for(link: str) -> str:
    if link.endswith("/"):
        return PREVIEW_URL + link.lstrip("/") + "index.html"
    return PREVIEW_URL + link.lstrip("/") + ".html"


def _pdf_is_shell(pdf: Path) -> bool:
    """打印时样式未应用会产生「导航壳页」——检测第 1 页是否为壳。"""
    from pypdf import PdfReader

    try:
        reader = PdfReader(str(pdf))
        if not reader.pages:
            return True
        text = reader.pages[0].extract_text() or ""
        return any(m in text for m in SHELL_MARKERS)
    except Exception:
        return True


def print_to_pdf(edge: str, url: str, out_pdf: Path, profile_root: Path, tag: str) -> None:
    """打印单页；每次用独立 profile 防止 Edge 进程复用导致的样式竞态，
    出现导航壳页则换 profile 重试。"""
    last_err: Exception | None = None
    last_stderr = ""
    for attempt in range(3):
        profile = profile_root / f"{tag}-{attempt}"
        profile.mkdir(parents=True, exist_ok=True)
        cmd = [
            edge,
            "--headless",
            "--disable-gpu",
            "--no-sandbox",
            f"--user-data-dir={profile}",
            f"--print-to-pdf={out_pdf.as_posix()}",
            "--no-pdf-header-footer",
            "--virtual-time-budget=15000",
            "--run-all-compositor-stages-before-draw",
            "--hide-scrollbars",
            url,
        ]
        try:
            r = subprocess.run(cmd, check=True, capture_output=True, timeout=90)
            last_stderr = (r.stderr or b"").decode("utf-8", errors="replace")[-2000:]
        except subprocess.CalledProcessError as e:
            last_err = e
            last_stderr = (e.stderr or b"").decode("utf-8", errors="replace")[-2000:]
            continue
        except Exception as e:  # noqa: BLE001
            last_err = e
            continue
        if not _pdf_is_shell(out_pdf):
            return
        out_pdf.unlink(missing_ok=True)
    raise RuntimeError(
        f"打印失败（样式未应用）: {url} ({last_err})"
        f"；最后一次 stderr: {last_stderr or '（空）'}"
    )


def wait_port(port: int, timeout: float = 60.0) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        with socket.socket() as s:
            if s.connect_ex(("127.0.0.1", port)) == 0:
                return
        time.sleep(0.5)
    raise TimeoutError(f"预览服务端口 {port} 未就绪")


def check_preview(index_url: str) -> None:
    """端口通了还要确认跑的是本站预览，而非被其他服务占用。"""
    import urllib.request

    try:
        with urllib.request.urlopen(index_url, timeout=10) as resp:
            body = resp.read(65536).decode("utf-8", errors="replace")
    except Exception as e:  # noqa: BLE001
        raise RuntimeError(f"预览首页请求失败: {index_url} ({e})") from e
    if "VitePress" not in body and BASE.rstrip("/") not in body:
        raise RuntimeError(f"{PORT} 端口被其他服务占用（响应中未发现 VitePress 特征）")


def _kill_process_tree(proc: subprocess.Popen) -> None:
    """结束预览服务。Windows 上 terminate 只杀 cmd.exe 外壳，
    必须 taskkill /T 清掉整棵进程树，否则留下 npm/node 僵尸进程。"""
    if proc.poll() is not None:
        return
    if sys.platform == "win32":
        subprocess.run(
            ["taskkill", "/T", "/F", "/PID", str(proc.pid)],
            capture_output=True,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
    else:
        proc.terminate()
    try:
        proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        proc.kill()


def page_filename(i: int, link: str) -> str:
    name = link.strip("/").replace("/", "_") or "index"
    return f"{i:03d}-{name}.pdf"


def compute_fingerprint(pages: list[str]) -> str:
    """pages 对应 dist HTML 的 (相对路径, size, mtime) 指纹。

    只算本书页面（约 65 个文件），不扫整个 dist——
    任何正文改版都会改变 size/mtime，使打印缓存失效。
    """
    import hashlib

    entries: list[tuple[str, int, int]] = []
    for link in pages:
        rel = link.strip("/")
        html = DIST / rel / "index.html" if link.endswith("/") else DIST / f"{rel}.html"
        if not html.exists():
            raise SystemExit(
                f"!! 构建产物缺失: {html.relative_to(ROOT)}（请重新 npm run docs:build）"
            )
        st = html.stat()
        entries.append((html.relative_to(DIST).as_posix(), st.st_size, st.st_mtime_ns))
    payload = "\n".join(f"{p}\t{s}\t{m}" for p, s, m in sorted(entries))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def ensure_pages(pages: list[str], edge: str, skip_print: bool) -> list[Path]:
    """打印（或复用缓存）全部正文页，返回与 pages 对齐的 PDF 路径。

    缓存 manifest 存 {links, fingerprint}：链接方案与站点内容都未变才复用，
    任一不匹配即视为过期（旧格式 manifest 直接视为不匹配）。
    """
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    fingerprint = compute_fingerprint(pages)
    manifest = {"links": pages, "fingerprint": fingerprint}
    cached = False
    if MANIFEST.exists():
        try:
            old = json.loads(MANIFEST.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            old = None
        cached = (
            isinstance(old, dict)
            and old.get("links") == pages
            and old.get("fingerprint") == fingerprint
        )
    paths = [CACHE_DIR / page_filename(i + 1, p) for i, p in enumerate(pages)]
    if cached and all(p.exists() for p in paths):
        print(f"== 复用打印缓存（{len(paths)} 页）==")
        return paths
    if skip_print:
        raise SystemExit(
            "--skip-print：打印缓存已过期（分页方案或站点内容有变更），"
            "请去掉 --skip-print 重新完整导出"
        )

    print(f"== 打印 {len(pages)} 页（预计 5–8 分钟）==")
    profile_root = Path(tempfile.mkdtemp(prefix="edge-profiles-"))
    preview = subprocess.Popen(
        "npm run docs:preview", cwd=ROOT, shell=True,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
    )
    try:
        wait_port(PORT)
        check_preview(PREVIEW_URL)
        for i, (link, out) in enumerate(zip(pages, paths), 1):
            print(f"[{i:02d}/{len(pages):02d}] {link} ... ", end="", flush=True)
            print_to_pdf(edge, url_for(link), out, profile_root, f"{i:03d}")
            print("ok")
    finally:
        _kill_process_tree(preview)
        shutil.rmtree(profile_root, ignore_errors=True)

    MANIFEST.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return paths


def make_cover_html(title: str, subtitle: str, body_pages: int) -> str:
    return f"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<style>
  @page {{ size: A4; margin: 0; }}
  body {{ font-family: "Source Han Serif SC","Noto Serif CJK SC",serif;
         margin: 0; height: 297mm; display: flex; flex-direction: column;
         justify-content: center; align-items: center; text-align: center;
         color: #1a1a1a; }}
  .rule {{ width: 60mm; border-top: 2px solid #b8860b; margin: 8mm 0; }}
  h1 {{ font-size: 30pt; letter-spacing: 4pt; margin: 0; font-weight: 700; }}
  h2 {{ font-size: 15pt; font-weight: 400; color: #333; letter-spacing: 2pt; margin: 4mm 0; }}
  h3 {{ font-size: 11pt; font-weight: 400; color: #666; margin: 2mm 0; }}
  p {{ font-size: 10pt; color: #666; line-height: 2; }}
</style></head><body>
<h1>架构炼金术</h1><div class="rule"></div>
<h2>{title}</h2>
<h3>{subtitle}</h3>
<p>正文 {body_pages} 页</p>
<div class="rule"></div>
<p>作者：Steven Tsai · v1.0 · {time.strftime("%Y-%m-%d")}<br>
开源仓库 github.com/StevenTsai/distribution-architecture<br>
Apache License 2.0</p>
</body></html>"""


def doc_title(link: str) -> str:
    """书签/目录标题：取 dist HTML 的第一个 h1 纯文本，失败退回链接末段。"""
    from html import unescape

    rel = link.strip("/")
    html_path = (DIST / rel / "index.html") if link.endswith("/") else DIST / f"{rel}.html"
    if html_path.exists():
        src = html_path.read_text(encoding="utf-8", errors="replace")
        m = re.search(r"<h1[^>]*>(.*?)</h1>", src, re.S)
        if m:
            inner = re.sub(r"<a[^>]*header-anchor[^>]*>.*?</a>", "", m.group(1), flags=re.S)
            text = unescape(re.sub(r"<[^>]+>", "", inner))
            text = re.sub(r"\s+", " ", text).strip().rstrip("#").strip()
            if text:
                return text
    return rel.split("/")[-1] or "首页"


def make_toc_html(offsets: list[tuple[str, int, list[tuple[str, int]]]],
                  toc_pages: int) -> str:
    """目录页 HTML。offsets 为 (组题, 组正文偏移, [(篇题, 篇正文偏移)])。

    页码为最终文档的 1 起绝对页码：封面 1 页 + 目录 toc_pages 页 + 正文偏移。
    行高固定，把预估 toc_pages 代入生成后打印实测页数，迭代到收敛。
    """
    from html import escape

    def disp(off: int) -> int:
        return toc_pages + off + 2

    rows: list[str] = []
    for gtitle, goff, docs in offsets:
        rows.append(
            f'<div class="grp"><span class="t">{escape(gtitle)}</span>'
            f'<span class="d"></span><span class="n">{disp(goff)}</span></div>'
        )
        for dtitle, doff in docs:
            rows.append(
                f'<div class="doc"><span class="t">{escape(dtitle)}</span>'
                f'<span class="d"></span><span class="n">{disp(doff)}</span></div>'
            )
    return f"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<style>
  @page {{ size: A4; margin: 16mm 18mm; }}
  body {{ font-family: "Source Han Serif SC","Noto Serif CJK SC",serif;
         margin: 0; color: #1a1a1a; font-size: 10.5pt; }}
  h1 {{ font-size: 19pt; letter-spacing: 8pt; text-align: center; font-weight: 700;
       margin: 0 0 7mm; padding-bottom: 4mm; border-bottom: 2px solid #b8860b; }}
  .grp, .doc {{ display: flex; align-items: baseline; }}
  .grp {{ font-weight: 700; font-size: 11.5pt; margin-top: 5mm; }}
  .doc {{ font-weight: 400; font-size: 10pt; margin-top: 2mm; padding-left: 6mm; }}
  .grp .t, .doc .t {{ flex: 0 1 auto; white-space: nowrap; overflow: hidden;
                     text-overflow: ellipsis; max-width: 80%; }}
  .d {{ flex: 1 1 auto; min-width: 8mm; margin: 0 2mm;
       border-bottom: 1.5px dotted #999; transform: translateY(-3px); }}
  .n {{ flex: 0 0 auto; font-variant-numeric: tabular-nums; color: #333; }}
</style></head><body>
<h1>目 录</h1>
{''.join(rows)}
</body></html>"""


def stamp_page_numbers(src: Path, dst: Path) -> bool:
    """除封面外每页底部居中盖页码（Helvetica，base-14 无需嵌入字体）。

    用 pymupdf 就地插入文本；库缺失或盖章失败返回 False，调用方原样透传。
    """
    try:
        import fitz  # pymupdf 的兼容入口
    except ImportError:
        try:
            import pymupdf as fitz  # 新版包直导名
        except ImportError:
            return False
    try:
        doc = fitz.open(src.as_posix())
        for i in range(1, doc.page_count):
            page = doc[i]
            label = str(i + 1)
            tw = fitz.get_text_length(label, fontsize=9)
            page.insert_text(
                ((page.rect.width - tw) / 2, page.rect.height - 18),
                label, fontsize=9, color=(0.45, 0.45, 0.45),
            )
        doc.save(dst.as_posix())  # 不用 garbage/deflate，避免动原有内容流
        doc.close()
        return True
    except Exception as e:  # noqa: BLE001
        print(f"\n!! 页码盖章失败（{e}），本次输出无页码", file=sys.stderr)
        return False


def merge_book(body_pdf_paths: list[Path], groups: list[tuple[str, list[str]]],
               group_counts: list[int], out_pdf: Path, edge: str, tag: str,
               title: str, subtitle: str, doc_titles: dict[str, str]) -> None:
    """合并正文 → 封面 + 目录（迭代页码收敛）→ 盖页码 → 书签/元数据。

    书签与元数据放在最后一步由 pypdf 写入：页码盖章要经 pymupdf 重存，
    关键信息最后写可保证不被中间环节丢失。
    """
    from pypdf import PdfReader, PdfWriter

    # 1) 正文合并，得知正文页数与 组/篇 的正文内偏移
    body = PdfWriter()
    for p in body_pdf_paths:
        body.append(str(p))
    body_pages = len(body.pages)
    del body

    offsets: list[tuple[str, int, list[tuple[str, int]]]] = []
    cur = 0
    for gtitle, links in groups:
        goff = cur
        docs = [(doc_titles[l], cur + i) for i, l in enumerate(links)]
        cur += len(links)
        offsets.append((gtitle, goff, docs))

    front_dir = Path(tempfile.mkdtemp(prefix="front-"))
    try:
        profiles = front_dir / "profiles"

        # 2) 封面（本地 HTML，打印快且无壳页风险）
        cover_html = front_dir / f"{tag}.html"
        cover_html.write_text(make_cover_html(title, subtitle, body_pages), encoding="utf-8")
        cover_pdf = cover_html.with_suffix(".pdf")
        print_to_pdf(edge, cover_html.as_uri(), cover_pdf, profiles, tag)

        # 3) 目录：预估页数生成 → 打印实测 → 不符则按实测重生成，直到收敛
        toc_pdf = front_dir / f"{tag}-toc.pdf"
        toc_html = front_dir / f"{tag}-toc.html"
        guess, actual = 1, 1
        for _ in range(3):
            toc_html.write_text(make_toc_html(offsets, guess), encoding="utf-8")
            toc_pdf.unlink(missing_ok=True)
            print_to_pdf(edge, toc_html.as_uri(), toc_pdf, profiles, f"{tag}-toc")
            actual = len(PdfReader(str(toc_pdf)).pages)
            if actual == guess:
                break
            guess = actual
        else:
            print(f"\n!! {tag}: 目录页数未收敛（实测 {actual} vs 预估 {guess}），"
                  f"目录页码可能整体偏移", file=sys.stderr)
        toc_pages = actual
        if toc_pages > 1:
            print(f"[目录 {toc_pages} 页] ", end="", flush=True)

        # 4) 拼装 封面+目录+正文（暂不加书签/元数据）
        basic = PdfWriter()
        basic.append(str(cover_pdf))
        basic.append(str(toc_pdf))
        for p in body_pdf_paths:
            basic.append(str(p))
        basic_pdf = front_dir / "basic.pdf"
        with basic_pdf.open("wb") as f:
            basic.write(f)
        del basic

        # 5) 逐页盖页码（跳过封面）
        stamped_pdf = front_dir / "stamped.pdf"
        if not stamp_page_numbers(basic_pdf, stamped_pdf):
            shutil.copy(basic_pdf, stamped_pdf)

        # 6) 最终输出：两级书签（分组 → 逐篇）+ PDF 元数据
        writer = PdfWriter()
        writer.append(str(stamped_pdf))
        book_title = ("架构炼金术" if title == "架构炼金术"
                      else f"架构炼金术 · {title}")
        writer.add_metadata({
            "/Title": book_title,
            "/Author": "Steven Tsai",
            "/Subject": subtitle,
            "/Keywords": "架构炼金术, 分布式架构, Spring Boot, 数据权限, "
                         "事件驱动, ADR, 架构治理",
            "/Creator": "scripts/export-pdf.py",
        })
        writer.add_outline_item("封面", 0)
        writer.add_outline_item("目录", 1)
        for (gtitle, goff, docs), count in zip(offsets, group_counts):
            assert len(docs) == count, "分组偏移与分组页数不一致"
            gitem = writer.add_outline_item(gtitle, 1 + toc_pages + goff)
            for dtitle, doff in docs:
                writer.add_outline_item(dtitle, 1 + toc_pages + doff, parent=gitem)
        with out_pdf.open("wb") as f:
            writer.write(f)
    finally:
        shutil.rmtree(front_dir, ignore_errors=True)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--build", action="store_true", help="导出前先 npm run docs:build")
    ap.add_argument("--skip-print", action="store_true", help="只用打印缓存重新合并分册")
    args = ap.parse_args()

    if args.build:
        print("== 构建站点 ==")
        subprocess.run("npm run docs:build", cwd=ROOT, shell=True, check=True)
    if not (DIST / "index.html").exists():
        print("!! 未找到构建产物，请先 npm run docs:build", file=sys.stderr)
        return 1

    groups = book_groups()
    pages = [link for _, links in groups for link in links]
    doc_titles = {l: doc_title(l) for l in pages}
    print(f"== 全书 {len(groups)} 组 / {len(pages)} 页 ==")

    edge = find_edge()
    OUT_DIR.mkdir(exist_ok=True)
    body_paths = ensure_pages(pages, edge, args.skip_print)

    # 每册的正文切片（与 groups 对齐）
    g_start: dict[str, int] = {}
    idx = 0
    for title, links in groups:
        g_start[title] = idx
        idx += len(links)

    for filename, title, subtitle, sel in OUTPUTS:
        if sel is None:
            sel_groups = groups
        else:
            sel_groups = [(t, l) for t, l in groups if t in sel]
            unknown = set(sel) - {t for t, _ in groups}
            if unknown:
                print(f"!! {filename}: 未找到分组 {unknown}", file=sys.stderr)
                return 1
        counts = [len(l) for _, l in sel_groups]
        first = g_start[sel_groups[0][0]]
        slice_paths = body_paths[first:first + sum(counts)]
        out_pdf = OUT_DIR / f"{filename}.pdf"
        print(f"== 合并 {filename}（{sum(counts)} 页正文）== ", end="", flush=True)
        merge_book(slice_paths, sel_groups, counts, out_pdf, edge,
                   re.sub(r"\W+", "", filename), title,
                   subtitle.replace("{n}", str(len(pages))), doc_titles)
        size_mb = out_pdf.stat().st_size / 1024 / 1024
        from pypdf import PdfReader
        n = len(PdfReader(str(out_pdf)).pages)
        print(f"ok → {n} 页 / {size_mb:.1f} MB")

    print("== 全部完成 ==")
    return 0


if __name__ == "__main__":
    sys.exit(main())
