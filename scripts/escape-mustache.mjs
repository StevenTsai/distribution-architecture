// 将 markdown 正文（非代码区）中的 {{...}} 占位符包上 <span v-pre>，
// 避免 VitePress 的 Vue 编译管道把模板占位符当插值表达式解析。
// 代码块（```）与行内代码（`...`）中的内容原样保留——它们已被 VitePress 自动加 v-pre。
import { readFileSync, writeFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

function collect(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) collect(p, acc)
    else if (p.endsWith('.md')) acc.push(p)
  }
  return acc
}

const files = collect('playbooks/takeover-engineering')
let totalReplaced = 0
const leftovers = []

for (const file of files) {
  const lines = readFileSync(file, 'utf8').split('\n')
  let inFence = false
  const out = lines.map((line, i) => {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence
      return line
    }
    if (inFence) return line

    // 保护行内代码段，处理完再还原
    const codeSpans = []
    let work = line.replace(/`[^`]*`/g, (m) => {
      codeSpans.push(m)
      return `__CODE${codeSpans.length - 1}__`
    })

    work = work.replace(/\{\{([^{}]*)\}\}/g, (_, inner) => {
      totalReplaced++
      return `<span v-pre>{{${inner}}}</span>`
    })

    if (/\{\{|\}\}/.test(work)) {
      leftovers.push(`${file}:${i + 1}: ${line}`)
    }

    return work.replace(/__CODE(\d+)__/g, (_, n) => codeSpans[+n])
  })
  writeFileSync(file, out.join('\n'))
}

console.log(`replaced: ${totalReplaced}`)
if (leftovers.length) {
  console.log('leftover (unpaired, needs manual check):')
  leftovers.forEach((l) => console.log('  ' + l))
}
