import { ESLint } from 'eslint'
import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const app = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const root = resolve(app, '../../../..')
const output = resolve(root, 'docs/experimentos/resultados/iso25010/complejidad')
// Zero reports every function, including complexity 1. Inline directives must
// not suppress this measurement. Tests and tooling are outside production src.
const eslint = new ESLint({
  cwd: app,
  allowInlineConfig: false,
  overrideConfig: { rules: { complexity: ['error', { max: 0, variant: 'classic' }] } },
})
const results = await eslint.lintFiles(['src/**/*.{ts,tsx}'])
const methods = []
for (const result of results) {
  for (const message of result.messages) {
    if (message.ruleId !== 'complexity') {
      if (message.severity === 2 || message.fatal) throw new Error(`${result.filePath}: ${message.message}`)
      continue
    }
    const match = message.message.match(/complexity of (\d+)\./)
    if (!match) throw new Error(`Unrecognized complexity diagnostic: ${message.message}`)
    methods.push({
      file: relative(root, result.filePath).split('\\').join('/'),
      line: message.line, column: message.column,
      complexity: Number(match[1]), message: message.message,
    })
  }
}
if (!methods.length) throw new Error('No functions measured; refusing an empty success')
methods.sort((a, b) => a.file.localeCompare(b.file, 'en') || a.line - b.line || a.column - b.column)
const maximum = Math.max(...methods.map(method => method.complexity))
const status = maximum < 10 ? 'CUMPLE' : 'NO CUMPLE'
mkdirSync(output, { recursive: true })
writeFileSync(resolve(output, 'webapp-complexity.json'), JSON.stringify({
  tool: 'ESLint', version: ESLint.version, rule: 'complexity', variant: 'classic',
  scope: 'src/**/*.{ts,tsx}', files_analyzed: results.length,
  functions_analyzed: methods.length, max_method_complexity: maximum,
  objective: '<10', status, methods,
}, null, 2) + '\n')
const summaryPath = resolve(output, 'summary.csv')
const lines = readFileSync(summaryPath, 'utf8').replace(/^\uFEFF/, '').trimEnd().split(/\r?\n/)
if (lines[0] !== '"module","max_method_complexity","objective","status","report"') {
  throw new Error('Unexpected summary.csv schema; regenerate the backend summary first')
}
const backend = lines.slice(1).filter(line => !/^"?webapp"?,/.test(line))
const web = `"webapp","${maximum}","<10","${status}","webapp-complexity.json"`
writeFileSync(summaryPath, [lines[0], ...backend, web, ''].join('\n'))
const message = `Web: ${methods.length} funciones, máximo ${maximum}, objetivo <10: ${status}.`
console.log(message)
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${message}\n`)
if (status !== 'CUMPLE') process.exitCode = 1
