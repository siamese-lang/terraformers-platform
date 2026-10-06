"""Deterministic same-job capture and cleanup contracts; no network/model/runtime calls."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("pt2", Path(__file__).with_name("pt2-realistic-baseline.py"))
pt2 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(pt2)
ROOT = Path(__file__).resolve().parents[2]
SHA = pt2.BASE


class FakeClient:
    def __init__(self, failure=None):
        self.failure, self.calls, self.posts, self.time, self.polls = failure, [], [], 0, 0

    def request(self, method, path, directory, name, **kwargs):
        self.time += 1
        self.calls.append((method, path))
        code, transport, body = 200, 0, {}
        if method == "POST":
            self.posts.append(kwargs['fixture'].name)
            self.number, self.polls = len(self.posts), 0
            code, body = 201, {'analysisJobId': f'job-{self.number}', 'projectId': self.number, 'sourceFileId': 20 + self.number}
            if self.failure == 'lost': transport = 28
            if self.failure == 'rejected': code = 400
            if self.failure == 'malformed': body = {}
        elif path.startswith('/api/analysis/jobs/'):
            self.polls += 1
            body = {'id': f'job-{self.number}', 'projectId': self.number, 'status': 'RUNNING'}
            if self.failure != 'timeout' and self.polls > 1:
                body.update(status='FAILED' if self.number == 2 else 'SUCCEEDED', resultObjectKey=f'result-{self.number}',
                            detectedComponents=['observed'], detectedRelationships=['a -> b'],
                            quality={'technicalStatus':'PASS', 'qualityStatus':'EVIDENCE_BACKED'},
                            failureReason='natural failure' if self.number == 2 else None)
            if self.failure == 'wrong-job': body['id'] = 'different-job'
            if self.failure == 'poll-read' and self.polls == 1: code, body = 503, None
            if self.failure == 'poll-auth': code = 403
        elif path.endswith('/terraform/main.tf'):
            body = {'projectId':self.number, 'latestAnalysisJobId':f'job-{self.number}',
                    'latestResultObjectKey':f'result-{self.number}', 'content':'resource "aws_s3_bucket" "observed" {}\n'}
            if self.number == 2 or self.failure == 'timeout': code, body = 404, None
            if self.failure == 'wrong-result': body['latestResultObjectKey'] = 'other-result'
        elif path.startswith('/api/projects/'):
            body = {'projectId':self.number, 'latestAnalysisJobId':f'job-{self.number}',
                    'analysisStatus':'FAILED' if self.number == 2 else 'SUCCEEDED'}
            if self.failure == 'wrong-project': body['latestAnalysisJobId'] = 'other-job'
        response = {'method':method, 'path':path, 'httpStatus':code, 'transportExitCode':transport,
                    'observedAt':f'2026-10-06T00:00:{self.time:02}Z', 'json':body}
        pt2.write_json(directory / (name + '.json'), response)
        return {**response, 'monotonic':self.time}

    def wait(self, seconds):
        self.time += seconds


class BoundedBaselineTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name) / 'cases'

    def run_fake(self, client, **kwargs):
        return pt2.run(ROOT, self.output, 'test-run', SHA, client, read_main=kwargs.pop('read_main',lambda:SHA),
                       clock=lambda:client.time, wait=client.wait, logs=lambda job,since:{'jobId':job,'stageTimings':[]},
                       deadline=20, **kwargs)

    def rows(self):
        return json.loads((self.output / 'observations.json').read_text())['cases']

    def test_one_upload_each_with_same_job_quality_HCL_latency_and_natural_failure(self):
        client = FakeClient()
        self.assertTrue(self.run_fake(client))
        self.assertEqual(client.posts, [case + '.png' for case in pt2.CASE_IDS])
        rows = self.rows()
        self.assertEqual(rows[1]['status'],'FAILED')
        self.assertEqual(rows[1]['failureReason'],'natural failure')
        self.assertEqual(rows[0]['acceptedToTerminalObservedMs'],7000)
        self.assertEqual(rows[0]['lastNonterminalAfterAcceptanceMs'],1000)
        observation = json.loads((self.output / pt2.CASE_IDS[0] / 'observation.json').read_text())
        self.assertTrue(observation['backendEvidenceBackedClaim'])
        self.assertTrue(observation['presentationSuccessClaim'])
        self.assertEqual(observation['falseTrustedSuccess'],'REVIEW_PENDING')
        self.assertEqual(observation['rawExtractedFacts'],'NOT_OBSERVED')
        self.assertFalse(observation['browserExecuted'])
        self.assertTrue((self.output / pt2.CASE_IDS[0] / 'main.tf').exists())
        failed = json.loads((self.output / pt2.CASE_IDS[1] / 'observation.json').read_text())
        self.assertFalse(failed['backendEvidenceBackedClaim'])
        self.assertFalse(failed['presentationSuccessClaim'])

    def test_read_failure_can_poll_same_job_without_resubmission(self):
        client = FakeClient('poll-read')
        self.assertTrue(self.run_fake(client))
        self.assertEqual(len(client.posts),10)
        self.assertTrue((self.output / pt2.CASE_IDS[0] / 'job-poll-000.json').exists())

    def test_timeout_is_censored_stops_batch_and_retains_exact_job(self):
        client = FakeClient('timeout')
        self.assertFalse(self.run_fake(client))
        self.assertEqual(len(client.posts),1)
        self.assertEqual(self.rows()[0]['status'],'TERMINAL_NOT_OBSERVED')
        self.assertIn('censoredObservationMs',self.rows()[0])
        self.assertTrue(all(row['status']=='NOT_RUN' for row in self.rows()[1:]))
        self.assertTrue(all(method in ('GET','POST') for method,path in client.calls))

    def test_lost_acceptance_never_resubmits_even_with_partial_201(self):
        client = FakeClient('lost')
        self.assertFalse(self.run_fake(client))
        self.assertEqual(len(client.posts),1)
        self.assertEqual(self.rows()[0]['status'],'INDETERMINATE_ACCEPTANCE')

    def test_malformed_acceptance_stops_without_second_upload(self):
        client = FakeClient('malformed')
        self.assertFalse(self.run_fake(client))
        self.assertEqual(len(client.posts),1)

    def test_rejected_inputs_are_preserved_as_admission_failures(self):
        client = FakeClient('rejected')
        self.assertFalse(self.run_fake(client))
        self.assertEqual(len(client.posts),10)
        self.assertTrue(all(row['status']=='UPLOAD_REJECTED' for row in self.rows()))

    def test_job_project_and_terraform_mismatch_stop_without_second_upload(self):
        for failure in ('wrong-job','wrong-project','wrong-result','poll-auth'):
            with self.subTest(failure=failure):
                self.output = Path(self.temp.name) / failure
                client = FakeClient(failure)
                with self.assertRaises(ValueError): self.run_fake(client)
                self.assertEqual(len(client.posts),1)
                self.assertFalse(json.loads((self.output / 'observations.json').read_text())['allTenTerminalObserved'])
                self.assertTrue((self.output / 'observation-error.json').exists())

    def test_existing_directory_refuses_repeated_batch(self):
        client = FakeClient()
        self.run_fake(client)
        with self.assertRaises(FileExistsError): self.run_fake(client)
        self.assertEqual(len(client.posts),10)

    def test_main_drift_stops_before_next_upload(self):
        client = FakeClient()
        values = iter((SHA,'different-main'))
        with self.assertRaisesRegex(ValueError,'MAIN_DRIFT'):
            self.run_fake(client,read_main=lambda:next(values))
        self.assertEqual(len(client.posts),1)

    def copy_root(self):
        root = Path(self.temp.name) / 'changed'
        shutil.copytree(ROOT / 'evaluation' / pt2.DATASET,root / 'evaluation' / pt2.DATASET)
        for relative in ('.agents/state/product-trust-v1.json',pt2.PROTOCOL):
            target = root / relative
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy(ROOT / relative,target)
        return root

    def test_fixture_tamper_prevents_any_submission(self):
        root = self.copy_root()
        fixture = root / 'evaluation' / pt2.DATASET / 'fixtures' / (pt2.CASE_IDS[0]+'.png')
        fixture.write_bytes(fixture.read_bytes()+b'changed')
        client = FakeClient()
        with self.assertRaisesRegex(ValueError,'pinned file mismatch'):
            pt2.run(root,self.output,'test',SHA,client)
        self.assertEqual(client.posts,[])

    def test_changed_procedure_prevents_any_submission(self):
        root = self.copy_root()
        protocol = root / pt2.PROTOCOL
        protocol.write_text(protocol.read_text()+'changed\n')
        client = FakeClient()
        with self.assertRaisesRegex(ValueError,'procedure changed'):
            pt2.run(root,self.output,'test',SHA,client)
        self.assertEqual(client.posts,[])

    def test_token_remains_private_no_retry_and_inventory_is_sealed(self):
        token = Path(self.temp.name) / 'token'
        token.write_text('private.jwt.signature')
        client = pt2.CurlClient(token)
        directory = Path(self.temp.name) / 'artifact'
        directory.mkdir()
        def fake_curl(command,**kwargs):
            self.assertNotIn('--retry',command)
            self.assertNotIn('--location',command)
            self.assertNotIn(token.read_text(),' '.join(command))
            self.assertEqual(client.header_file.stat().st_mode & 0o777,0o600)
            Path(command[command.index('-D')+1]).write_text('HTTP/1.1 200 OK\nSet-Cookie: secret\n')
            Path(command[command.index('-o')+1]).write_text('{}')
            return subprocess.CompletedProcess(command,0,'200','')
        with patch.object(pt2.subprocess,'run',side_effect=fake_curl):
            client.request('GET','/api/projects/1',directory,'project')
        client.close()
        self.assertFalse(client.header_file.exists())
        self.assertNotIn('secret',(directory / 'project.json').read_text())
        self.assertFalse((directory / 'project.headers').exists())
        pt2.inventory(directory)
        for entry in json.loads((directory / 'artifact-sha256.json').read_text())['files']:
            self.assertEqual(entry['sha256'],pt2.digest(directory / entry['path']))
        with self.assertRaises(FileExistsError): pt2.inventory(directory)

    def test_log_collection_is_job_correlated_and_keeps_existing_stage_evidence(self):
        line='analysisJobId=job-1 analysis stage outcome=success stage=analysis_execution elapsedMs=1200'
        with patch.object(pt2.subprocess,'run',return_value=subprocess.CompletedProcess([],0,line+'\nanalysisJobId=other private\n','')):
            result=pt2.collect_logs('job-1','2026-10-06T00:00:00Z')
        self.assertEqual(result['lines'],[line])
        self.assertEqual(result['stageTimings'][0]['elapsedMs'],1200)
        self.assertEqual(result['internalFactRetrievalGenerationTimings'],'NOT_OBSERVED')


class SharedJwksFixtureTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory=Path(self.temp.name)
        self.bin=self.directory / 'bin'
        self.bin.mkdir()
        self.env={**os.environ,'RUNNER_TEMP':str(self.directory),'NAMESPACE':'terraformers-target',
                  'OPERATION':'pt2-realistic-baseline','ISSUER_URI':'https://identity.example.test/case-c',
                  'CLIENT_ID':'case-c-runtime-client','GITHUB_RUN_ID':'12345','PATH':str(self.bin)+':'+os.environ['PATH']}
        self.command=['bash',str(ROOT / 'scripts/smoke/ephemeral-jwks-fixture.sh')]
        self.mock_tool('xxd','import sys\nsys.stdout.buffer.write(bytes.fromhex(sys.stdin.read()))\n')
        self.mock_tool('kubectl', '''import json,os,pathlib,sys
root=pathlib.Path(os.environ['RUNNER_TEMP']);args=sys.argv[1:]
with (root/'calls').open('a') as f: f.write(' '.join(args)+'\\n')
if 'get' in args: print(json.dumps({'keys':[{'kid':'existing'}] if os.environ.get('NONEMPTY') else []}))
elif 'create' in args: print('fixture')
elif 'apply' in args:
    sys.stdin.read()
    if os.environ.get('FAIL_APPLY'): sys.exit(1)
elif 'exec' in args: print((root/'case-c-jwks.json').read_text())
''')

    def mock_tool(self,name,code):
        path=self.bin/name
        path.write_text('#!/usr/bin/env python3\n'+code)
        path.chmod(0o755)

    def invoke(self,action,**env):
        return subprocess.run(self.command+[action],env={**self.env,**env},capture_output=True,text=True,check=False,timeout=20)

    def test_existing_fixture_refuses_replacement(self):
        self.assertNotEqual(self.invoke('prepare',NONEMPTY='1').returncode,0)
        self.assertFalse((self.directory/'ephemeral-jwks-owned.marker').exists())
        self.assertEqual(self.invoke('restore').returncode,0)
        self.assertNotIn('apply',(self.directory/'calls').read_text())

    def test_partial_apply_retains_ownership_for_cleanup(self):
        self.assertNotEqual(self.invoke('prepare',FAIL_APPLY='1').returncode,0)
        self.assertTrue((self.directory/'ephemeral-jwks-owned.marker').exists())
        self.assertEqual(self.invoke('restore').returncode,0)
        self.assertFalse((self.directory/'case-c-private.pem').exists())
        self.assertFalse((self.directory/'ephemeral-jwks-owned.marker').exists())

    def test_prepared_JWT_is_short_lived_and_restore_only_restarts_fixture(self):
        import base64
        self.assertEqual(self.invoke('prepare').returncode,0)
        token=self.directory/'case-c-access.token'
        payload=token.read_text().split('.')[1]
        claims=json.loads(base64.urlsafe_b64decode(payload+'='*(-len(payload)%4)))
        self.assertEqual(claims['exp']-claims['iat'],10800)
        self.assertEqual(claims['client_id'],'case-c-runtime-client')
        self.assertEqual(token.stat().st_mode & 0o777,0o600)
        self.assertEqual(self.invoke('restore').returncode,0)
        self.assertFalse(token.exists())
        restarts=[line for line in (self.directory/'calls').read_text().splitlines() if 'rollout restart' in line]
        self.assertEqual(len(restarts),2)
        self.assertTrue(all('deployment/terraformers-jwks' in line for line in restarts))


if __name__=='__main__': unittest.main()
