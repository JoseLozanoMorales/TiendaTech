import { ESLint } from 'eslint'
import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const app = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const root = resolve(app, '../../../..')
const output = resolve(root, 'docs/experimentos/resultados/iso25010/complejidad')
const eslint = new ESLint({ cwd: app })
const results = await eslint.lintFiles(['.'])
const totals = results.reduce((sum, result) => ({
  files: sum.files + 1,
  errors: sum.errors + result.errorCount,
  warnings: sum.warnings + result.warningCount,
}), { files: 0, errors: 0, warnings: 0 })
const portable = results.map(result => ({
  ...result,
  filePath: relative(root, result.filePath).split('\\').join('/'),
}))
mkdirSync(output, { recursive: true })
writeFileSync(resolve(output, 'webapp-eslint.json'), JSON.stringify(portable, null, 2) + '\n')
const status = totals.files > 0 && totals.errors === 0 && totals.warnings === 0 ? 'CUMPLE' : 'NO CUMPLE'
writeFileSync(resolve(output, 'webapp-eslint-summary.csv'),
  'module,tool,files_analyzed,errors,warnings,objective,status,report\n' +
  `webapp,ESLint,${totals.files},${totals.errors},${totals.warnings},0 errors and 0 warnings,${status},webapp-eslint.json\n`)
// Preserve the historical PMD rows; their metric is not an ESLint error count.
const summaryPath = resolve(output, 'summary.csv')
const lines = readFileSync(summaryPath, 'utf8').replace(/^\uFEFF/, '').trimEnd().split(/\r?\n/)
const extended = lines[0].includes('files_analyzed')
const header = extended ? lines[0] : lines[0] + ',"tool","files_analyzed","errors","warnings"'
const backend = lines.slice(1).filter(line => !/^"?webapp"?,/.test(line))
  .map(line => extended ? line : line + ',"PMD",,,')
const web = `"webapp","","0 errors and 0 warnings","${status}","webapp-eslint.json","ESLint","${totals.files}","${totals.errors}","${totals.warnings}"`
writeFileSync(summaryPath, [header, ...backend, web, ''].join('\n'))
const message = `ESLint web: ${totals.files} archivos, ${totals.errors} errores, ${totals.warnings} advertencias. ${status}.`
console.log(message)
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Análisis estático web\n\n${message}\n\nNo es una medición de complejidad ciclomática.\n`)
if (status !== 'CUMPLE') process.exitCode = 1
