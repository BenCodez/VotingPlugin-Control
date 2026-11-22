# VotingPlugin-Control WebUI Audit

Run the workflow on demand in Cezar from a trusted, reviewed revision. The check launches Playwright Chromium, signs in, visits available sections at desktop and mobile widths, and writes a private page report to the run's Assets directory. It counts console, API and network failures without recording raw messages, request bodies, response payloads, cookies or storage state. Some configuration and diagnostic views need an inspection POST. The audit blocks those requests, so a blank or unavailable value from one of those views remains unverified.

Install Playwright and its matching Chromium on the check runner. The product server and VotingPlugin do not need browser dependencies. Configure these project secrets with access limited to checks:

- `VOTINGPLUGIN_CONTROL_WEBUI_PASSWORD` for the owner password. Enter it directly through the operator's secret settings.
- `VP_CONTROL_AUDIT_ORIGIN` for a verified HTTPS origin or a literal loopback endpoint on a trusted tunnel.

The workflow explicitly binds these names to one check. Check-only access controls injection, and the check runner must also be isolated from coding agents at the operating-system account or sandbox boundary. Do not attach a production credential to a vault or check process readable by coding agents. Review the exact workflow and script before attaching production secrets. Run it only on a trusted revision from an owner-controlled runner. Credentials must never be passed as arguments, printed, or put in client configuration. The runner blocks redirects, cross-origin requests, WebSockets and writes after the one login request.

The standalone command is `node scripts/webui-audit.cjs`. Supply the same two settings out-of-band and set `VP_CONTROL_AUDIT_OUTPUT` to a new directory under a private parent. The optional `--synthetic-fixture` flag captures screenshots for synthetic loopback fixtures only. Live screenshots require page-specific redaction and remain outside this runner's current output.
