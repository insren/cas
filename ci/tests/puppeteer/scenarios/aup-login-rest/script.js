const puppeteer = require('puppeteer');
const cas = require('../../cas.js');
const assert = require("assert");
const express = require('express');

(async () => {
    let aupAccepted = false;

    let app = express();
    app.post("/aup", (req, res) => {
        console.log(`Accepting AUP...`);
        aupAccepted = true;
        res.status(200).send("Accepted");
    });
    app.get("/aup/status", (req, res) => {
        console.log(`AUP status: ${aupAccepted}`);
        if (aupAccepted) {
            res.status(202).send("Accepted");
        } else {
            res.status(403).send("Denied");
        }
    });
    app.get("/aup/policy", (req, res) => {
        try {
            console.log("Received AUP policy terms request");
            const data = {
                "@class": "org.apereo.cas.aup.AcceptableUsagePolicyTerms",
                "code": "screen.aup.policyterms.some.key",
                "defaultText": "Default policy text"
            };
            res.json(data);
        } catch (e) {
            throw e;
        }
    });
    
    let server = app.listen(5544, async () => {
        const browser = await puppeteer.launch(cas.browserOptions());
        const page = await cas.newPage(browser);
        const service = "http://localhost:9889/anything/app1";
        await cas.goto(page, `https://localhost:8443/cas/login?service=${service}`);
        await cas.loginWith(page);
        await cas.assertTextContent(page, "#main-content #login #fm1 h3", "Acceptable Usage Policy");
        await cas.assertVisibility(page, 'button[name=submit]');
        await cas.assertVisibility(page, 'button[name=cancel]');
        await page.waitForTimeout(1000);
        await cas.click(page, "#aupSubmit");
        await page.waitForNavigation();
        let ticket = await cas.assertTicketParameter(page);
        let body = await cas.doRequest(`https://localhost:8443/cas/p3/serviceValidate?service=${service}&ticket=${ticket}&format=JSON`);
        await cas.logg(body);
        let authenticationSuccess = JSON.parse(body).serviceResponse.authenticationSuccess;
        assert(authenticationSuccess.user === "casuser");

        console.log("Logging in again, now with SSO");
        await cas.goto(page, `https://localhost:8443/cas/login?service=${service}`);
        await cas.assertTicketParameter(page);
        await cas.goto(page, `https://localhost:8443/cas/logout`);

        console.log("Logging in again, now without SSO");
        await cas.goto(page, `https://localhost:8443/cas/login?service=${service}`);
        await cas.loginWith(page);
        await cas.assertTicketParameter(page);

        server.close(() => {
            console.log('Exiting server...');
            browser.close();
        });
    });
})();
