#!/usr/bin/env python3
"""One PT-6 real-browser journey. No application API mocks, cloud calls, or automatic retries.

Requires the existing backend package/test-compile, fixture-configured frontend build,
Python Playwright and Chromium. See docs/evaluation/product-trust-pt-6-browser-protocol.md.
"""
import argparse
from contextlib import nullcontext
import hashlib
import http.client
import json
import mimetypes
import os
import re
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
from urllib.request import urlopen

from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[2]
DATASET = ROOT / 'evaluation/terraformers-realistic-v1'
IDENTITY = '04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014'
INPUT = DATASET / 'fixtures/pt1-06-thumbnail-pipeline.png'
INPUT_SHA = 'b41f87b2f8afa2fb60496b8a68289d804748a6d881d8d46cba2df13241b6578c'
ISSUER = 'https://identity.example.test/portable-authenticated'
CLIENT = 'portable-runtime-client'
COGNITO = 'https://cognito-idp.ap-northeast-2.amazonaws.com/'
STEPS = ['authentication', 'one_upload', 'pending_navigation_reload', 'terminal_draft', 'other_identity_denial']
TOOL_PINS = {'TERRAFORM_VERSION': '1.8.5',
             'TERRAFORM_SHA256': 'bb1ee3e8314da76658002e2e584f2d8854b6def50b7f124e27b957a42ddacfea',
             'AWS_PROVIDER_VERSION': '5.100.0',
             'AWS_PROVIDER_SHA256': '1589a2266af699cbd5d80737a0fe02e54ec9cf2ca54e7e00ac51c7359056f274'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def verified_local_tools(cache):
    check(cache.is_relative_to('/workspace') or cache.is_relative_to('/tmp'), 'Tools must be in a writable local cache')
    dockerfile = (ROOT / 'backend/Dockerfile').read_text()
    for key, value in TOOL_PINS.items():
        check(re.search(r'^ARG ' + key + '=' + re.escape(value) + r'$', dockerfile, re.M), 'Dockerfile pin mismatch: ' + key)
    binary = cache / 'bin/terraform'
    plugin_dir = cache / 'terraform-plugins'
    provider = plugin_dir / 'registry.terraform.io/hashicorp/aws/5.100.0/linux_amd64/terraform-provider-aws_v5.100.0_x5'
    bindings = {}
    for name, executable, member, pin in [('terraform', binary, 'terraform', 'TERRAFORM_SHA256'),
                                         ('aws-provider', provider, provider.name, 'AWS_PROVIDER_SHA256')]:
        archive = cache / 'downloads' / (name + '.zip')
        with archive.open('rb') as stream:
            archive_sha = hashlib.file_digest(stream, 'sha256').hexdigest()
        check(archive_sha == TOOL_PINS[pin], 'Pinned archive checksum mismatch: ' + name)
        check(executable.resolve().is_relative_to(cache) and os.access(executable, os.X_OK), 'Local executable missing: ' + name)
        with zipfile.ZipFile(archive) as zipped, zipped.open(member) as stream:
            member_sha = hashlib.file_digest(stream, 'sha256').hexdigest()
        with executable.open('rb') as stream:
            executable_sha = hashlib.file_digest(stream, 'sha256').hexdigest()
        check(executable_sha == member_sha, 'Extracted executable differs from verified archive: ' + name)
        bindings[name] = {'archiveSha256': archive_sha, 'executableSha256': executable_sha, 'path': str(executable)}
    version = subprocess.run([str(binary), 'version', '-json'], env={'CHECKPOINT_DISABLE': '1'},
                             check=True, capture_output=True, text=True, timeout=10)
    check(json.loads(version.stdout)['terraform_version'] == '1.8.5', 'Terraform version mismatch')
    bindings.update({'terraformVersion': '1.8.5', 'awsProviderVersion': '5.100.0',
                     'providerVersionBinding': 'Exact Dockerfile archive checksum and extracted executable bytes',
                     'implementation': 'TerraformCliValidator', 'executor': 'ProcessCommandExecutor'})
    return binary, plugin_dir, bindings


def journey(out, cache):
    out.mkdir(parents=True, exist_ok=False)  # Never overwrite an earlier outcome.
    result = {'procedure': 'docs/evaluation/product-trust-pt-6-browser-protocol.md',
              'correctiveProcedure': 'docs/evidence/product-trust-pt-6/correction-1/procedure.md',
              'humanAuthorizedCorrectiveIteration': 1,
              'executionBaseSha': '37e006be4d5995019704ea8a4009e1a59da039b2',
              'datasetIdentity': IDENTITY, 'input': str(INPUT.relative_to(ROOT)), 'inputSha256': INPUT_SHA,
              'modelUnderTestRunCount': 0, 'liveCloudActionCount': 0, 'automaticRetries': 0,
              'steps': {step: 'NOT_RUN' for step in STEPS}, 'outcome': 'INCOMPLETE',
              'timingScope': 'LOCAL_STUB_INCLUDING_CONTROLLED_EXECUTOR_HOLD_NOT_PRODUCTION_LATENCY'}
    events, identity_events, observations = [], [], []
    blocked, jwks_reads = [], []
    backend = server = browser = temp = playwright_driver = None
    current_step = None
    try:
        check(sha((DATASET / 'candidate-identity.json').read_bytes()) == IDENTITY, 'Frozen identity mismatch')
        for item in json.loads((DATASET / 'candidate-identity.json').read_text())['files']:
            check(sha((DATASET / item['path']).read_bytes()) == item['sha256'], 'Frozen file mismatch: ' + item['path'])
        check(sha(INPUT.read_bytes()) == INPUT_SHA, 'Input bytes mismatch')
        binary, plugin_dir, tool_bindings = verified_local_tools(cache)
        result['localValidatorTools'] = tool_bindings
        jar = ROOT / 'backend/target/terraformers-backend-modernization-0.1.0-SNAPSHOT.jar'
        build = ROOT / 'frontend/build'
        classes = ROOT / 'backend/target/test-classes/com/terraformers/modernization/analysis'
        check(jar.is_file() and (build / 'index.html').is_file(), 'Build prerequisites missing')
        result['bindings'] = {'jarSha256': sha(jar.read_bytes()),
                              'frontendFiles': {str(p.relative_to(build)): sha(p.read_bytes())
                                                for p in sorted(build.rglob('*')) if p.is_file()},
                              'sources': {p: sha((ROOT / p).read_bytes()) for p in [
                                  'scripts/checks/product-trust-browser-journey.py',
                                  'scripts/smoke/ephemeral-jwks-fixture.sh',
                                  'backend/src/test/java/com/terraformers/modernization/analysis/BrowserJourneyFixture.java',
                                  'docs/evaluation/product-trust-pt-6-browser-protocol.md',
                                  'docs/evidence/product-trust-pt-6/correction-1/procedure.md']}}
        # Retain local logs on failure until process cleanup and sanitized evidence capture.
        with nullcontext(tempfile.mkdtemp(prefix='pt6-private-')) as private:
            temp = Path(private)
            subprocess.run(['bash', str(ROOT / 'scripts/smoke/ephemeral-jwks-fixture.sh'), 'local-browser'],
                           env={**os.environ, 'RUNNER_TEMP': private, 'ISSUER_URI': ISSUER, 'CLIENT_ID': CLIENT},
                           check=True, timeout=20, capture_output=True)
            tokens = {owner: {use: (temp / f'pt6-{owner}-{use}.token').read_text()
                              for use in ('access', 'id')} for owner in ('owner', 'other')}
            isolated = temp / 'classes/com/terraformers/modernization/analysis'
            isolated.mkdir(parents=True)
            compiled = list(classes.glob('BrowserJourneyFixture*.class'))
            check(bool(compiled), 'Isolated test fixture must be compiled')
            for source in compiled:
                shutil.copyfile(source, isolated / source.name)
            result['bindings']['fixtureClasses'] = {source.name: sha(source.read_bytes()) for source in compiled}
            port = free_port()

            class Handler(BaseHTTPRequestHandler):
                def log_message(self, *args):
                    pass  # Never log headers, request bodies, or bearer tokens.

                def handle_request(self):
                    path = urlsplit(self.path).path
                    if path == '/jwks.json' and self.command == 'GET':
                        jwks_reads.append(time.time())
                        body, status, headers = (temp / 'case-c-jwks.json').read_bytes(), 200, [('Content-Type', 'application/json')]
                    elif path.startswith('/api/'):
                        # Ordinary same-origin reverse proxy: preserve Host and Origin, no identity injection.
                        conn = http.client.HTTPConnection('127.0.0.1', port, timeout=15)
                        payload = self.rfile.read(int(self.headers.get('Content-Length', '0')))
                        forwarded = {k: v for k, v in self.headers.items()
                                     if k.lower() not in ('connection', 'content-length', 'transfer-encoding')}
                        conn.request(self.command, self.path, body=payload, headers=forwarded)
                        response = conn.getresponse()
                        status, body, headers = response.status, response.read(), response.getheaders()
                        conn.close()
                        events.append({'method': self.command, 'path': path, 'status': status,
                                       'responseBytes': len(body), 'responseSha256': sha(body)})
                    else:
                        resolved = (build / path.lstrip('/')).resolve()
                        if not resolved.is_relative_to(build.resolve()):
                            status, body, headers = 403, b'', []
                        else:
                            target = resolved if resolved.is_file() else build / 'index.html'
                            body, status = target.read_bytes(), 200
                            headers = [('Content-Type', mimetypes.guess_type(target)[0] or 'application/octet-stream')]
                    self.send_response(status)
                    for key, value in headers:
                        if key.lower() not in ('content-length', 'transfer-encoding', 'connection'):
                            self.send_header(key, value)
                    self.send_header('Content-Length', str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)

                do_GET = do_POST = do_PATCH = do_PUT = do_DELETE = handle_request

            server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
            origin = f'http://127.0.0.1:{server.server_port}'
            threading.Thread(target=server.serve_forever, daemon=True).start()
            args = ['java', '-Dloader.path=' + str(temp / 'classes'),
                    '-Dloader.main=com.terraformers.modernization.analysis.BrowserJourneyFixture', '-cp', str(jar),
                    'org.springframework.boot.loader.launch.PropertiesLauncher', '--spring.profiles.active=local,browser-journey',
                    '--server.address=127.0.0.1', f'--server.port={port}', '--terraformers.security.jwt.enabled=true',
                    '--terraformers.security.jwt.issuer-uri=' + ISSUER,
                    '--terraformers.security.jwt.jwk-set-uri=' + origin + '/jwks.json',
                    '--terraformers.security.jwt.cognito.client-id=' + CLIENT,
                    '--terraformers.storage.reader-provider=filesystem', '--terraformers.storage.writer-provider=filesystem',
                    '--terraformers.storage.filesystem.root-path=' + str(temp / 'objects'),
                    '--browser-journey.terraform-binary=' + str(binary),
                    '--browser-journey.plugin-dir=' + str(plugin_dir),
                    '--terraformers.analysis.dispatch-enabled=false', '--browser-journey.release-marker=' + str(temp / 'release')]
            with (temp / 'backend.log').open('w') as log:
                backend = subprocess.Popen(args, stdout=log, stderr=subprocess.STDOUT)
                deadline = time.monotonic() + 60
                while True:
                    check(backend.poll() is None, 'Backend startup failed; see sanitized startup evidence')
                    try:
                        with urlopen(f'http://127.0.0.1:{port}/actuator/health', timeout=1) as health:
                            if health.status == 200:
                                break
                    except OSError:
                        pass
                    check(time.monotonic() < deadline, 'Backend readiness exceeded 60 seconds')
                    time.sleep(.2)

                with nullcontext(sync_playwright().start()) as playwright:
                    playwright_driver = playwright
                    browser = playwright.chromium.launch(executable_path='/usr/bin/chromium', headless=True,
                                                         args=['--no-sandbox'])
                    result['browserVersion'] = browser.version

                    def new_page(owner):
                        context = browser.new_context(viewport={'width': 1280, 'height': 900})

                        def route_request(route):
                            request = route.request
                            if request.url.startswith(origin + '/'):
                                route.continue_()  # Every application response is from the real server.
                                return
                            if request.url.startswith(COGNITO):
                                operation = request.headers.get('x-amz-target', '').split('.')[-1]
                                data = request.post_data_json
                                response = None
                                if (operation == 'InitiateAuth' and data.get('AuthFlow') == 'USER_SRP_AUTH'
                                        and data.get('ClientId') == CLIENT
                                        and data.get('AuthParameters', {}).get('USERNAME') == owner + '@example.test'):
                                    response = {'AuthenticationResult': {'AccessToken': tokens[owner]['access'],
                                                'IdToken': tokens[owner]['id'], 'ExpiresIn': 1200, 'TokenType': 'Bearer',
                                                'RefreshToken': 'unused-local-fixture-refresh'}}
                                elif operation == 'GetUser' and data.get('AccessToken') == tokens[owner]['access']:
                                    response = {'Username': owner + '@example.test', 'UserAttributes': [
                                        {'Name': 'sub', 'Value': 'pt6-browser-' + owner},
                                        {'Name': 'email', 'Value': owner + '@example.test'},
                                        {'Name': 'nickname', 'Value': 'PT6 ' + owner}]}
                                identity_events.append({'identity': owner, 'operation': operation, 'supported': response is not None})
                                if response is None:
                                    route.abort()
                                else:
                                    route.fulfill(status=200, content_type='application/x-amz-json-1.1', body=json.dumps(response))
                                return
                            blocked.append(urlsplit(request.url).netloc)
                            route.abort()

                        context.route('**/*', route_request)
                        page = context.new_page()
                        page.set_default_timeout(10000)
                        return page

                    def api(page, path, method='GET', data=None, mode='session'):
                        answer = page.evaluate('''async ({path, method, data, mode}) => {
                          let token = Object.keys(localStorage).filter(k => k.endsWith('.accessToken')).map(k => localStorage[k])[0];
                          if (mode === 'forged') token = token.split('.').slice(0, 2).join('.') + '.AAAA';
                          const headers = {};
                          if (mode !== 'anonymous') headers.Authorization = 'Bearer ' + token;
                          if (data !== null) headers['Content-Type'] = 'application/json';
                          const r = await fetch(path, {method, headers, body: data === null ? undefined : JSON.stringify(data)});
                          const bytes = new Uint8Array(await r.arrayBuffer());
                          const digest = await crypto.subtle.digest('SHA-256', bytes);
                          return {status: r.status, bytes: bytes.length,
                            sha256: Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join(''),
                            text: r.headers.get('Content-Type')?.includes('image/') ? null : new TextDecoder().decode(bytes)};
                        }''', {'path': path, 'method': method, 'data': data, 'mode': mode})
                        observations.append({'method': method, 'path': path, 'auth': mode, **answer})
                        return answer

                    def read(page, path):
                        answer = api(page, path)
                        check(answer['status'] == 200, 'Owner read rejected: ' + path)
                        return json.loads(answer['text'])

                    def login(page, owner):
                        page.goto(origin + '/login')
                        page.get_by_label('Email', exact=True).fill(owner + '@example.test')
                        page.get_by_label('Password', exact=True).fill('LocalFixtureOnly-NotAnIdPPassword')
                        # Finish the UI's actual first identity-persistence request before adding
                        # supplemental audit probes. Do not manufacture concurrent first-user calls.
                        with page.expect_response(lambda r: urlsplit(r.url).path == '/api/users/me/display-name'
                                                  and r.request.method == 'PATCH') as profile:
                            page.get_by_role('button', name='Login', exact=True).click()
                        check(profile.value.status == 204, 'Real UI profile persistence failed')
                        page.wait_for_url('**/generate')

                    current_step = 'authentication'
                    owner = new_page('owner')
                    login(owner, 'owner')
                    check(read(owner, '/api/projects') == [], 'Owner inventory must start empty')
                    check(api(owner, '/api/projects', mode='anonymous')['status'] == 401, 'Anonymous protected route accepted')
                    check(api(owner, '/api/projects', mode='forged')['status'] == 401, 'Forged signature accepted')
                    check(bool(jwks_reads), 'Backend did not fetch public JWKS')
                    result['steps'][current_step] = 'PASS'

                    current_step = 'one_upload'
                    owner.get_by_label('프로젝트 이름').fill('PT6 bounded browser journey')
                    owner.get_by_label('PNG/JPEG architecture image').set_input_files(str(INPUT))
                    with owner.expect_response(lambda r: urlsplit(r.url).path == '/api/upload' and r.request.method == 'POST') as accepted:
                        owner.get_by_role('button', name='분석 시작', exact=True).click()
                    check(accepted.value.status == 201, 'Upload did not return 201')
                    upload = accepted.value.json()
                    result['upload'] = upload
                    pid, jid = upload['projectId'], upload['analysisJobId']
                    project_path, job_path = f'/api/projects/{pid}', f'/api/analysis/jobs/{jid}'
                    check(upload['binaryPersisted'] and upload['status'] == 'PENDING', 'Upload binding/storage/hold failed')
                    owner.wait_for_url(f'**/projects/{pid}')
                    result['steps'][current_step] = 'PASS'

                    current_step = 'pending_navigation_reload'
                    pending = read(owner, job_path)
                    project = read(owner, project_path)
                    check(project['latestAnalysisJobId'] == jid and project['sourceFileId'] == upload['sourceFileId'], 'Pending identity mismatch')
                    check(pending['status'] == 'PENDING' and project['analysisStatus'] == 'PENDING', 'Work claimed before release')
                    check(project['analysisTiming'] == pending['timing'], 'Project/job pending timing mismatch')
                    accepted_at = pending['timing']['acceptedAt']
                    expect(owner.get_by_text('분석 요청이 대기 중입니다.', exact=False)).to_be_visible()
                    check(owner.locator('time').first.get_attribute('datetime') == accepted_at, 'Acceptance anchor missing')
                    owner.screenshot(path=str(out / '01-pending.png'), full_page=True)
                    owner.get_by_role('link', name='내 프로젝트 목록으로 돌아가기', exact=True).click()
                    expect(owner.get_by_role('link', name='상세 보기', exact=True)).to_be_visible()
                    owner.get_by_role('link', name='상세 보기', exact=True).click()
                    expect(owner.get_by_text('분석 요청이 대기 중입니다.', exact=False)).to_be_visible()
                    owner.reload()
                    expect(owner.get_by_text('분석 요청이 대기 중입니다.', exact=False)).to_be_visible()
                    check(owner.locator('time').first.get_attribute('datetime') == accepted_at, 'Reload reset acceptance anchor')
                    expect(owner.get_by_text('접수 후 경과 (기기 시각 기준):', exact=False)).to_be_visible()
                    check(read(owner, job_path) == pending, 'Navigation/reload changed original pending job')
                    check(read(owner, project_path)['analysisTiming'] == pending['timing'], 'Reload changed durable timing')
                    owner.screenshot(path=str(out / '02-pending-after-reload.png'), full_page=True)
                    result['pendingJob'] = pending
                    result['steps'][current_step] = 'PASS'

                    current_step = 'terminal_draft'
                    (temp / 'release').touch()
                    result['releaseAtEpochSeconds'] = time.time()
                    deadline = time.monotonic() + 60
                    while True:
                        terminal = read(owner, job_path)
                        if terminal['status'] in ('SUCCEEDED', 'FAILED'):
                            break
                        check(time.monotonic() < deadline, 'Original job exceeded terminal wait; do not resubmit')
                        owner.wait_for_timeout(200)
                    check(terminal['status'] == 'SUCCEEDED', 'Original job terminal FAILED')
                    check(terminal['timing']['acceptedAt'] == accepted_at and terminal['timing']['terminalAt']
                          and terminal['timing']['acceptedToTerminalMs'] >= 0, 'Terminal timing not durable')
                    final_project = read(owner, project_path)
                    check(final_project['analysisTiming'] == terminal['timing'], 'Project/job terminal timing mismatch')
                    check(terminal['quality'] is None and final_project['quality'] is None, 'Stub fabricated quality')
                    terraform = read(owner, project_path + '/terraform/main.tf')
                    check(terraform['latestAnalysisJobId'] == jid and terraform['fileId'] == terminal['resultFileId']
                          == final_project['resultFileId'], 'Terraform artifact is not bound to the original job')
                    source = api(owner, project_path + '/source-image')
                    check(source['status'] == 200 and source['sha256'] == INPUT_SHA, 'Source image differs from accepted bytes')
                    expect(owner.get_by_text('분석 작업의 처리가 완료되었습니다.', exact=False)).to_be_visible(timeout=10000)
                    expect(owner.get_by_text('결과 품질: 평가 기록 없음', exact=True)).to_be_visible()
                    expect(owner.get_by_text('Terraform은 검토하고 수정할 수 있는 초안입니다.', exact=False)).to_be_visible()
                    expect(owner.get_by_text('실제 권한 유효성 또는 배포 성공이 보장되지 않습니다.', exact=False)).to_be_visible()
                    expect(owner.locator('.terraform-code code')).to_have_text(terraform['content'])
                    check(owner.locator('.terraform-code code').text_content() == terraform['content'], 'Displayed HCL bytes differ')
                    check(owner.locator('time').last.get_attribute('datetime') == terminal['timing']['terminalAt'], 'Terminal UI timestamp differs')
                    result.update({'terminalJob': terminal, 'terminalProject': final_project, 'terraform': terraform,
                                   'visibleTerminalText': owner.locator('.analysis-status').inner_text()})
                    owner.screenshot(path=str(out / '03-terminal-draft.png'), full_page=True)
                    result['steps'][current_step] = 'PASS'

                    current_step = 'other_identity_denial'
                    other = new_page('other')
                    login(other, 'other')
                    other.goto(origin + f'/projects/{pid}')
                    expect(other.get_by_role('alert')).to_be_visible()
                    check(other.locator('.terraform-code, img').count() == 0, 'Private artifact visible to other identity')
                    other.screenshot(path=str(out / '04-other-identity-denied.png'), full_page=True)
                    probes = [(project_path, 'GET', None), (f'/api/project-tree/{pid}', 'GET', None),
                              (project_path + '/source-image', 'GET', None), (project_path + '/terraform/main.tf', 'GET', None),
                              (job_path, 'GET', None), (project_path + '/visibility', 'PATCH', {'visibility': 'PUBLIC'}),
                              (project_path + '/terraform/main.tf', 'PUT', {'content': '# forbidden replacement'}),
                              ('/api/analysis/jobs', 'POST', {'projectId': pid, 'sourceFileId': upload['sourceFileId']}),
                              (project_path, 'DELETE', None)]
                    for path, method, data in probes:
                        check(api(other, path, method, data)['status'] == 403, 'Other identity operation not denied: ' + method + ' ' + path)
                    check(api(other, project_path, mode='anonymous')['status'] == 403, 'Anonymous private metadata accepted')
                    check(api(other, job_path, mode='anonymous')['status'] == 401, 'Anonymous job accepted')
                    check(read(owner, job_path) == terminal, 'Denied mutations changed original job')
                    check(read(owner, project_path) == final_project and final_project['visibility'] == 'PRIVATE', 'Denied mutations changed project')
                    check(read(owner, project_path + '/terraform/main.tf') == terraform, 'Denied mutation changed HCL')
                    check(len(read(owner, '/api/projects')) == 1 and read(other, '/api/projects') == [], 'Owned inventory changed')
                    check(api(owner, project_path + '/source-image')['sha256'] == INPUT_SHA, 'Denied mutations changed source')
                    check(len([e for e in events if e['method'] == 'POST' and e['path'] == '/api/upload']) == 1, 'More than one upload attempt')
                    check(not [e for e in events if e['method'] == 'POST' and e['path'] == '/api/analysis/jobs' and e['status'] != 403], 'Additional job submission accepted')
                    check(not blocked and all(e['supported'] for e in identity_events), 'Unexpected external/identity operation')
                    result['steps'][current_step] = 'PASS'
                    result['outcome'] = 'PASS_PENDING_INDEPENDENT_REVIEW'
                    browser.close()
                    browser = None
                result['serverStages'] = [line for line in (temp / 'backend.log').read_text().splitlines()
                                          if 'analysisJobId=' + jid in line]
                result['jwksFetchCount'] = len(jwks_reads)
                backend.terminate()
                backend.wait(timeout=10)
                backend = None
                server.shutdown()
                server.server_close()
                server = None
    except Exception as error:
        result['outcome'] = 'FAIL_PRESERVED_NO_RETRY'
        result['failure'] = {'step': current_step or 'startup', 'type': type(error).__name__, 'message': str(error)}
        if current_step:
            result['steps'][current_step] = 'FAIL'
    finally:
        if browser is not None:
            browser.close()
        if playwright_driver is not None:
            playwright_driver.stop()
        if backend is not None:
            backend.terminate()
            try:
                backend.wait(timeout=10)
            except subprocess.TimeoutExpired:
                backend.kill()
                backend.wait(timeout=5)
        if server is not None:
            server.shutdown()
            server.server_close()
        if temp is not None:
            log_path = temp / 'backend.log'
            if log_path.is_file():
                result['fixtureValidatorConfiguration'] = [line for line in log_path.read_text().splitlines()
                                                           if 'Browser fixture real validator=' in line]
            if log_path.is_file() and 'serverStages' not in result:
                result['serverStages'] = [line for line in log_path.read_text().splitlines()
                                         if 'ERROR' in line or 'APPLICATION FAILED' in line]
            shutil.rmtree(temp)
            result['temporaryMaterialRemoved'] = not temp.exists()
        result.update({'httpEvents': events, 'browserApiObservations': observations,
                       'identityTransportEvents': identity_events, 'blockedExternalHosts': blocked,
                       'jwksFetchCount': len(jwks_reads)})
        write_json(out / 'result.json', result)
        write_json(out / 'manifest.json', {p.name: sha(p.read_bytes()) for p in sorted(out.iterdir()) if p.is_file()})
    print(json.dumps({'outcome': result['outcome'], 'steps': result['steps'], 'evidence': str(out)}))
    return 0 if result['outcome'] == 'PASS_PENDING_INDEPENDENT_REVIEW' else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--terraform-cache', type=Path, required=True)
    arguments = parser.parse_args()
    raise SystemExit(journey(arguments.output.resolve(), arguments.terraform_cache.resolve()))
