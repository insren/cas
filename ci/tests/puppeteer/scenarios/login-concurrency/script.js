const assert = require('assert');
const fs = require('fs');
const {JSONPath} = require('jsonpath-plus');

const startPuppeteerLoadTest = require('puppeteer-loadtest');
let args = process.argv.slice(2);
const config = JSON.parse(fs.readFileSync(args[0]));
assert(config != null);

const paramOptions = {
    file: config.loadScript,
    samplesRequested: 5,
    concurrencyRequested: 5
};
const loadtest = async () => await startPuppeteerLoadTest(paramOptions);

loadtest().then(results => {
    console.log(JSON.stringify(results, null, 2));
    const samples = JSONPath({path: '$..sample', json: results });
    assert(samples.length === parseInt(paramOptions.samplesRequested))
});
