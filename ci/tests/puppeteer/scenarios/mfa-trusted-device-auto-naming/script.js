const puppeteer = require("puppeteer");
const assert = require("assert");
const cas = require("../../cas.js");
const path = require("path");
const fs = require("fs");
const os = require("os");

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    console.log("Fetching Scratch codes from /cas/actuator...");
    let scratch = await cas.fetchGoogleAuthenticatorScratchCode();

    const page = await cas.newPage(browser);

    await cas.goto(page, "https://localhost:8443/cas/login");
    await cas.loginWith(page, "casuser", "Mellon");
    
    console.log(`Using scratch code ${scratch} to login...`);
    await cas.type(page,'#token', scratch);
    await cas.pressEnter(page);
    await page.waitForNavigation();
    
    await cas.assertCookie(page);

    const baseUrl = "https://localhost:8443/cas/actuator/multifactorTrustedDevices";
    let response = await cas.doRequest(baseUrl);
    let record = JSON.parse(response)[0];
    assert(record.id !== null);
    assert(record.name !== null);

    response = await cas.doRequest(`${baseUrl}/${record.principal}`);
    record = JSON.parse(response)[0];
    console.dir(record, {depth: null, colors: true});
    assert(record.id !== null);
    assert(record.name !== null);

    await cas.doGet(`${baseUrl}/export`,
        res => {
            const tempDir = os.tmpdir();
            let exported = path.join(tempDir, 'trusteddevices.zip');
            res.data.pipe(fs.createWriteStream(exported));
            console.log(`Exported records are at ${exported}`);
        },
        error => {
            throw error;
        }, {}, "stream");

    let template = path.join(__dirname, 'device-record.json');
    let body = fs.readFileSync(template, 'utf8');
    console.log(`Import device record:\n${body}`);
    await cas.doRequest(`${baseUrl}/import`, "POST", {
        'Accept': 'application/json',
        'Content-Length': body.length,
        'Content-Type': 'application/json'
    }, 201, body);

    await browser.close();
})();
