import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";

const rulesDir = join(dirname(fileURLToPath(import.meta.url)), "..");
const readJson = (name) => JSON.parse(readFileSync(join(rulesDir, name), "utf8"));

const ajv = new Ajv2020({ allErrors: true });
addFormats(ajv);
const validate = ajv.compile(readJson("schema.json"));

const errorsOf = (data) => {
  validate(data);
  return ajv.errorsText(validate.errors, { separator: "\n" });
};

const countryFiles = readdirSync(rulesDir).filter((f) => /^[A-Z]{2}\.json$/.test(f));

test("existen las reglas de los países del MVP", () => {
  for (const file of ["CO.json", "PE.json", "MX.json"]) {
    assert.ok(countryFiles.includes(file), `falta rules/${file}`);
  }
});

for (const file of countryFiles) {
  test(`${file} cumple el schema`, () => {
    const rules = readJson(file);
    assert.ok(validate(rules), errorsOf(rules));
  });

  test(`${file} declara el país de su nombre de archivo`, () => {
    assert.equal(readJson(file).country, file.replace(".json", ""));
  });

  test(`${file} tiene un rango de estadía coherente`, () => {
    const { min, max } = readJson(file).stayDays;
    if (min !== null && max !== null) {
      assert.ok(min <= max, `stayDays.min (${min}) > stayDays.max (${max})`);
    }
  });
}

// El schema debe rechazar datos inválidos; si no, las pruebas anteriores no prueban nada.
const invalidCases = [
  ["país que no es ISO alpha-2", (r) => (r.country = "COL")],
  ["moneda que no es ISO 4217", (r) => (r.currency = "pesos")],
  ["tipo de autoridad desconocido", (r) => (r.refundAuthority.type = "bank")],
  ["unidad tributaria sin valor anual", (r) => delete r.amountUnit.value],
  ["moneda local con sigla de unidad tributaria", (r) => (r.amountUnit = { type: "local_currency", code: "UVT" })],
  ["vía de salida desconocida", (r) => r.exitModes.push("rail")],
  ["aeropuerto sin código IATA", (r) => delete r.refundPoints[0].iata],
  ["fuente sin url", (r) => delete r.sources[0].url],
  ["fecha con formato inválido", (r) => (r.validFrom = "01/01/2026")],
  ["campo obligatorio ausente", (r) => delete r.needsVerification],
  ["propiedad no definida en el schema", (r) => (r.passportNumber = "X")],
];

for (const [name, mutate] of invalidCases) {
  test(`el schema rechaza: ${name}`, () => {
    const rules = readJson("CO.json");
    mutate(rules);
    assert.equal(validate(rules), false);
  });
}
