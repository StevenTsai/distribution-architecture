# Contributing Guide

[中文](https://github.com/StevenTsai/distribution-architecture/blob/main/CONTRIBUTING.md) | English

Thank you for your interest in Distribution Architecture! Here is how you can contribute.

## How to Contribute

### Report an Issue

If you find a documentation error, a code bug, or have a suggestion for improvement:

1. Search GitHub Issues to see whether a similar issue already exists
2. If not, open a new issue containing:
   - A clear title and description
   - Reproduction steps (for bugs)
   - Expected behavior vs. actual behavior
   - Relevant environment information

### Submit a Pull Request

1. Fork this repository
2. Create your feature branch: `git checkout -b feature/my-improvement`
3. Commit your changes: `git commit -m 'Add some improvement'`
4. Push to the branch: `git push origin feature/my-improvement`
5. Open a Pull Request

### PR Guidelines

- **Title**: describe the change concisely, e.g. "Fix data permission SQL typo in tutorial 02"
- **Description**: explain why the change is needed and what it affects
- **Related issues**: reference any related issue in the description
- **One PR, one change**: avoid mixing unrelated changes in a single PR

## Types of Contributions

### Documentation

- Fix typos or grammar mistakes
- Improve the readability of code examples
- Add missing explanations or comments
- Translation (English documentation)

### Code (distribution-starter)

- Fix bugs
- Add new design pattern examples
- Improve test coverage
- Upgrade dependency versions

### Design

- Propose new Architecture Decision Records (ADRs)
- Suggest improvements to existing designs
- Share your experience building on top of this project

## Development Environment

### Documentation Project

The documentation consists of plain Markdown files that you can edit directly:

```bash
git clone https://github.com/your-username/distribution-architecture.git
cd distribution-architecture
# Edit the Markdown files directly
```

### Online Book (VitePress)

This project uses VitePress to build the online book, deployed to GitHub Pages.

**Local preview**:

```bash
npm install
npm run docs:dev    # Development mode (hot reload)
npm run docs:build  # Production build
npm run docs:preview # Preview the build output
```

**Adding a new page**:

1. Create a Markdown file in the appropriate directory (e.g. `architecture/new-pattern.md`)
2. Add a link to the `sidebar` in `.vitepress/config.mts`
3. Push to the main branch — deployment is automatic

**Visit the online book**: https://steventsai.github.io/distribution-architecture/

### Starter Project

```bash
cd distribution-starter
# Make sure Java 17+ and Maven 3.6+ are installed
mvn clean compile
mvn test
```

## Documentation Conventions

- Write in Chinese for the main documentation; English translations live under `en/`
- Use Java syntax highlighting for code examples
- Every design pattern document covers: problem statement, design decision, code example, trade-off analysis
- ADR documents follow the [ADR template](https://adr.github.io/)

## Code of Conduct

- Respect every contributor
- Give feedback constructively
- Keep discussions technical
- Beginners are welcome to ask questions

## License

By submitting a contribution, you agree that it will be released under the [Apache License 2.0](../LICENSE).
