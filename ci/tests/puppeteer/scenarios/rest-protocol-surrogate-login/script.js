const assert = require('assert');
const cas = require('../../cas.js');

(async () => {
    let params = new URLSearchParams();
    params.append('username', 'user1+casuser');
    params.append('password', 'Mellon');
    await cas.doPost("https://localhost:8443/cas/v1/users",
        params, {
            'Accept': 'application/json',
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        res => {
            console.log(res.data.authentication.attributes);
            assert(res.data.authentication.attributes.surrogateUser != null);
            assert(res.data.authentication.attributes.surrogateEnabled != null);
            assert(res.data.authentication.attributes.surrogatePrincipal != null);
        },
        error => {
            throw error;
        });

    await cas.doPost("https://localhost:8443/cas/v1/users",
        "username=casuser&password=Mellon", {
            'Accept': 'application/json',
            'X-Surrogate-Principal': 'user1',
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        res => {
            console.log(res.data.authentication.attributes);
            assert(res.data.authentication.attributes.surrogateUser != null);
            assert(res.data.authentication.attributes.surrogateEnabled != null);
            assert(res.data.authentication.attributes.surrogatePrincipal != null);
        },
        error => {
            throw error;
        });

    console.log("Getting ticket with surrogate principal");
    const tgt = await cas.doPost("https://localhost:8443/cas/v1/tickets",
        "username=casuser&password=Mellon", {
            'Accept': 'application/json',
            'X-Surrogate-Principal': 'user1',
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        res => res.data,
        error => {
            throw error;
        });
    console.log(`Received ticket-granting ticket ${tgt}`);

    const service = "https://example.org";
    const st = await cas.doPost(`https://localhost:8443/cas/v1/tickets/${tgt}`,
        `service=${service}`, {
            'Accept': 'application/json',
            'X-Surrogate-Principal': 'user1',
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        res => res.data,
        error => {
            throw error;
        });
    console.log(`Received service ticket ${st}`);

    let body = await cas.doRequest(`https://localhost:8443/cas/p3/serviceValidate?service=${service}&ticket=${st}&format=JSON`);
    await cas.logg(body);
    let json = JSON.parse(body.toString());
    let authenticationSuccess = json.serviceResponse.authenticationSuccess;
    assert(authenticationSuccess.attributes.employeeNumber !== undefined);
    assert(authenticationSuccess.attributes["fname"] === undefined);
    assert(authenticationSuccess.attributes["lname"] === undefined);

})();
