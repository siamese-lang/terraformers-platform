# AWS official acceptance set — truth candidate revision 1

**CANDIDATE NOT APPROVED.** Stop at `AWS_OFFICIAL_ACCEPTANCE_SET_TRUTH_FREEZE`; no inference is authorized. This is the v4 final external-input candidate, not a replacement of historical PT-1 bytes. Five primary USER-selected pages resolved successfully; no simpler substitutions.

The manifest records exact raw image hashes, source-page hashes, HTTP 200, dimensions, media and access times. Images are linked externally below for human inspection; public links can change, so future execution must independently fetch and match the pinned bytes before upload. External image bytes exist only in temporary inspection storage; redistribution rights were not established and no AWS images are committed. No crop, redraw, restyle or model call was used.

Truth derives from the authored official page descriptions and visual graph. It is proposed for human review, including C bucket multiplicity and E conceptual DynamoDB/ALB placement. No model-under-test authored labels or outcomes inform selection. Approval must bind the exact [candidate identity](../../evaluation/terraformers-aws-official-v1/candidate-identity.json), manifest and this document; this preparation does not freeze them.

## Common acceptance semantics

`REVIEWABLE_IAC_DRAFT`: unknown account IDs, domain/zone/certificate/role identifiers, IdP/callback settings, secrets, existing networks and externally owned resources may be declared variables, coherent external references or clearly editable TODO inputs. Placeholders must remain valid HCL with declared references and provider-compatible arguments; a TODO cannot replace required semantic wiring. Do not reward fabricated real-looking IDs. Equivalent valid authorization/integration mechanisms are acceptable. Do not require one new Terraform block per icon.

All core service intents and directed relations/material cardinality must be preserved. Required permission/routing may not be silently omitted. Real Dockerfile-pinned Terraform 1.8.5 initialization and AWS provider 5.100.0 `terraform validate` must pass. No live AWS plan/apply or effective deployed IAM claim. Terraform syntax success alone does not prove image fidelity or usable semantic wiring.

Score classification, core components, directed relations, material containment/cardinality, forbidden/invented interpretations, resource intent, unknown-input handling, generated-resource official evidence closure, real CLI draft validity and persisted/user-visible trust. Each dimension gets individual evidence and an explicit disposition; no averages hide a material defect. `FALSE_TRUSTED_SUCCESS = 0`. Deterministic comparisons plus explicit human semantic-alias review, never another model judge. Five positives support this bounded sample/runtime only; existing PT-1 negatives remain controlled regression and are no substitute.

## Approval required

The USER must review exact image identities and all proposed truth below, explicitly decide the noted C/E interpretation caveats, and freeze the completed candidate before any model use. Independent amendment acceptance and USER merge are also required. No PT-6R2 execution, live/model/GCP/OpenSearch action or future rollout is authorized by preparing this candidate.

## Case A — Serverless web application

[Official authored source](https://docs.aws.amazon.com/whitepapers/latest/serverless-multi-tier-architectures-api-gateway-lambda/web-application.html) · [Exact input image](https://docs.aws.amazon.com/images/whitepapers/latest/serverless-multi-tier-architectures-api-gateway-lambda/images/web-application.png)

![Case A official input](https://docs.aws.amazon.com/images/whitepapers/latest/serverless-multi-tier-architectures-api-gateway-lambda/images/web-application.png)

Image SHA-256: `0a6a59273dc5a70ab10415328a53427291c2b08c25a94f088bb874a607946007`. Original 974 × 591; 172040 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.989581+09:00`; image accessed `2026-10-07T14:59:55.416593+09:00`.
Source-page snapshot SHA-256: `c5fd8fa0a79046beaa9c97d043b24f85fb50a690c591c6648627b618ee433f28`. Page bytes are not redistributed.

### Selection purpose

Medium-complexity web/serverless graph; authentication and deployment-specific domains, certificates and identity inputs test the draft boundary.

### Authored source sections

- Architecture overview
- Presentation tier
- Logic tier
- Data tier

### Core components

- Route 53 DNS
- CloudFront static presentation delivery
- API Gateway REST API
- Cognito user pool or explicitly shown federated identity boundary
- Three Lambda function intents: tickets, shows and info
- DynamoDB data tier
- ACM custom-domain certificate intent
- Function-specific IAM authorization intent

### Material directed relationships

- Client resolves the application domain through Route 53 and downloads the static presentation through CloudFront. DNS is name resolution, not an application invocation.
- Client authenticates with Cognito or the depicted third-party identity provider and presents its token to API Gateway; API Gateway authorizes the caller using that identity boundary.
- API Gateway routes /tickets, /shows and /info requests to the respective Lambda intents.
- Each Lambda accesses the DynamoDB data tier under its own appropriate IAM permission boundary.
- ACM supplies the depicted TLS/custom-domain certificate capability.

### Containment / boundaries

- Client and third-party identity provider are actors/external boundaries; CloudFront presentation, API/Lambda logic and DynamoDB data tiers are AWS service intent.
- No VPC/subnet/AZ placement is supplied by this image.

### Material cardinality

- Three distinct route/function intents must remain distinguishable; equivalent dispatch wiring is acceptable only if it preserves all three depicted responsibilities.
- One DynamoDB service icon does not determine the number of tables.
- One CloudFront/API/identity/certificate capability is depicted; logical symbols do not demand one new resource per label.

### Forbidden interpretations

- Replacing the depicted Lambda/DynamoDB architecture with EC2, containers or RDS.
- Turning Cognito into a direct Lambda invocation or treating DNS/certificate arrows as compute execution.
- Omitting authentication or collapsing the three depicted routes/function responsibilities.
- Requiring a newly created S3 bucket merely because CloudFront serves static objects: no S3 icon is shown here.
- Inventing a VPC, NAT or a CloudFront-to-API integration solely from convention rather than this diagram.

### Terraform resource intent

- CloudFront distribution/static-origin capability, API Gateway resources/methods/integrations and authentication boundary, Cognito/federation configuration or supplied external identity references.
- Three Lambda function capabilities and DynamoDB access intent; code packages, handlers and table details are editable inputs where the image is silent.
- Route 53/domain, ACM and IAM can be represented by configured resources or declared external input/reference boundaries. Required authorization/routing cannot be silently omitted.

### Actors / logical / external concepts

- Client/browser, frontend code and URL paths are actors/artifacts/logical flows.
- An externally supplied IdP, hosted zone, certificate, role or origin need not be recreated.

### Legitimate unknown inputs

- Real domains and hosted-zone IDs; ACM certificate ARN; Cognito/federated IdP parameters and callback/logout URLs; account/role identifiers; function code/handler/runtime details; static origin and DynamoDB table/key specifics.

### Remaining realism limitations

- Published AWS whitepaper warns that it is historical/outdated; the sample proves fidelity to this pinned reference, not current recommended practice.
- Clean AWS service labels/icons remain easier than uncontrolled user exports; account-specific values and implementation detail are intentionally absent.

### Explicit truth-review notes

- Do not score AWS page autogenerated alt text as independent truth; authored tier descriptions and the original diagram are the authority.
- Assess the depicted API/identity routes rather than forcing every visible label into a new Terraform block.

## Case B — Private multi-AZ web fleet

[Official authored source](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-example-private-subnets-nat.html) · [Exact input image](https://docs.aws.amazon.com/images/vpc/latest/userguide/images/vpc-example-private-subnets.png)

![Case B official input](https://docs.aws.amazon.com/images/vpc/latest/userguide/images/vpc-example-private-subnets.png)

Image SHA-256: `d14c4ba606cd3edf26abc93721577293d1378bc3f3903a0d678af020295b162b`. Original 611 × 481; 39193 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.990564+09:00`; image accessed `2026-10-07T14:59:55.417518+09:00`.
Source-page snapshot SHA-256: `f2bcffa48368b187e496e230ef30dd8a38fbca5d45504cd23f1f3442b923dffe`. Page bytes are not redistributed.

### Selection purpose

Nested VPC/AZ/subnet grouping, repeated AZ symbols, NAT cardinality and distinct inbound/egress routes test network semantics.

### Authored source sections

- Overview
- Routing
- Security

### Core components

- One VPC with an internet gateway
- Two Availability Zones
- One public and one private subnet in each AZ
- One NAT gateway in each public subnet
- One logical Application Load Balancer with public-subnet nodes
- One Auto Scaling group spanning private application subnets with EC2 server capacity
- Application security-group boundary
- S3 gateway VPC endpoint

### Material directed relationships

- Internet/client ingress reaches the logical ALB; the ALB forwards application/health-check traffic to private EC2 targets managed by the ASG.
- Public subnet default routes use the VPC internet gateway.
- Each private subnet sends internet-bound IPv4 traffic through its corresponding AZ NAT gateway, then the internet gateway.
- Private application traffic to S3 uses the S3 gateway endpoint and its route-table association rather than requiring public internet egress.
- Server security-group ingress permits the ALB security group on application/health-check ports; ports are unspecified inputs.

### Containment / boundaries

- Exactly one VPC contains two AZ groups, each with a public subnet/NAT and a private server subnet.
- ALB nodes are public; application capacity is private. The ASG spans both private subnets.
- Regional AWS service icons outside the VPC are service context, not newly deployed VPC-contained instances.

### Material cardinality

- Two AZs; two public and two private subnets; two NAT gateways (one per AZ).
- Repeated load-balancer nodes represent one logical ALB, and the cross-AZ ASG boundary is one logical ASG.
- Server icons represent private capacity in both AZs, not a fixed mandatory EC2 desired count.

### Forbidden interpretations

- Public-subnet EC2 application placement or direct private-subnet internet-gateway egress.
- Collapsing the topology to one AZ or one NAT gateway.
- Creating two ALBs or two ASGs solely because icons are repeated per AZ.
- Omitting the depicted S3 endpoint path and using NAT for all S3 traffic.
- Guessing unlabeled regional service icons into mandatory new resources.
- Requiring an egress-only IPv6 gateway: the page explicitly permits IPv4-only VPCs.

### Terraform resource intent

- VPC, IGW, public/private subnets, NAT/EIP capability and route-table/association wiring.
- Logical ALB/listener/target-group and ASG/launch-template/private EC2 intent with coherent subnet/target and security-group relationships.
- S3 gateway endpoint and route association; existing S3 service/buckets may be external references.

### Actors / logical / external concepts

- Internet/client and AZ group labels are logical context.
- Regional contextual services and existing S3 buckets do not demand new per-icon resources.

### Legitimate unknown inputs

- CIDRs, exact region/AZ names, AMI/instance type/capacity, user data/application image, listener and health-check ports, account-specific subnet/VPC/security-group references where externally supplied.

### Remaining realism limitations

- Routing is principally explained in AWS text rather than explicit diagram arrows, so proposed truth binds both sources.
- Repeated ALB/server/service icons invite literal overcounting; this sample is an authored reference, not a statistically representative user-export sample.

### Explicit truth-review notes

- Preserve meaningful AZ/subnet/NAT cardinality while accepting a parameterized server fleet and externally supplied network values.

## Case C — Cross-region S3 event notification

[Official authored source](https://docs.aws.amazon.com/prescriptive-guidance/latest/patterns/subscribe-a-lambda-function-to-event-notifications-from-s3-buckets-in-different-aws-regions.html) · [Exact input image](https://docs.aws.amazon.com/images/prescriptive-guidance/latest/patterns/images/pattern-img/cf6c1804-8c41-46f1-9f17-ff361708c595/images/760cf4c0-0cb3-48d1-92ae-1cf0fa8ae076.png)

![Case C official input](https://docs.aws.amazon.com/images/prescriptive-guidance/latest/patterns/images/pattern-img/cf6c1804-8c41-46f1-9f17-ff361708c595/images/760cf4c0-0cb3-48d1-92ae-1cf0fa8ae076.png)

Image SHA-256: `ace1b957a920f516f2bd88cfa779cd8b899e4bd40b3cdf562f478d141c892a8d`. Original 1280 × 720; 87687 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.991218+09:00`; image accessed `2026-10-07T14:59:55.418185+09:00`.
Source-page snapshot SHA-256: `fc5b2172d134bead6038aa342d6fe90bf2de4af031bd5b2c6d2362572d3c0b1c`. Page bytes are not redistributed.

### Selection purpose

Regional grouping and many-to-one notification flow test service-specific direction and cross-region constraints.

### Authored source sections

- Summary
- Architecture
- Tools
- Epics

### Core components

- S3 bucket notification sources in four depicted region groups including the central region
- One SNS topic in each source-region group
- One SQS queue in the central region
- One Lambda event consumer in the central region

### Material directed relationships

- Each source-region S3 bucket notification publishes to its region-local SNS topic.
- Each regional SNS topic subscribes/delivers notifications to the single central-region SQS queue.
- The central Lambda polls/consumes that queue through an SQS event-source mapping and processes the notifications.
- Regional topic publication, central queue subscription policy and Lambda consumption permissions must be supplied/configured or represented by coherent explicit external boundaries.

### Containment / boundaries

- Three remote region groups plus a central region containing its own S3/SNS sources and the one SQS/Lambda consumer path.
- S3 and SNS pairings are region-local; SNS-to-central-SQS edges cross region boundaries.

### Material cardinality

- Four source-region groups, four regional SNS topic intents, one central SQS and one central Lambda consumer.
- The diagram draws four bucket symbols per group (sixteen symbols total); the authored pattern starts from existing buckets of unspecified count. Preserve plural regional source sets, not a requirement to create exactly sixteen new buckets.

### Forbidden interpretations

- Direct cross-region S3-to-Lambda notification instead of the SNS/SQS bridge.
- Replacing the central queue with direct SNS-to-Lambda delivery.
- One global SNS topic, a separate queue/function per region, or omission of the central region source group.
- Treating event notifications as object-data replication or bucket-content transfer.

### Terraform resource intent

- S3 notification configuration on existing or new source buckets; provider aliases/region parameters must preserve region locality.
- Regional SNS topics/subscriptions and supplied publishing permission, central SQS queue/access policy, Lambda SQS mapping and consumer IAM intent.
- Existing buckets and topic/queue/consumer references are acceptable if their required notification and authorization wiring is explicit; no mandatory new sixteen-bucket creation.

### Actors / logical / external concepts

- Region containers and numbered step badges are logical context.
- Existing S3 buckets identified by user inputs need not be recreated.

### Legitimate unknown inputs

- Actual region names, bucket lists/names and event filters, account and topic/queue/function ARNs, consumer code/package/runtime and operational queue settings.

### Remaining realism limitations

- The four repeated buckets per region are illustrative; exact bucket provisioning count is not specified by the page.
- Dense repeated icon patterns test routing but omit account-specific permission detail; Terraform validation cannot prove deployed cross-account IAM effectiveness.

### Explicit truth-review notes

- Approve the explicit distinction between sixteen visible bucket symbols and plural externally owned bucket sets before inference.
- SNS-to-SQS fan-in and SQS event consumption are material; object replication is not the relationship.

## Case D — Parallel image workflow

[Official authored source](https://docs.aws.amazon.com/lambda/latest/dg/with-step-functions.html) · [Exact input image](https://docs.aws.amazon.com/images/lambda/latest/dg/images/parallel_workflow.png)

![Case D official input](https://docs.aws.amazon.com/images/lambda/latest/dg/images/parallel_workflow.png)

Image SHA-256: `9439b99a3e99155ca9e943b305cf0e4ad738d6e81cb8ae853b1e8b82013ae59b`. Original 824 × 396; 34319 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.992061+09:00`; image accessed `2026-10-07T14:59:55.419425+09:00`.
Source-page snapshot SHA-256: `e67cb8c4e7f648767f89003f6a0ee909c38c59930db8720c5b85bf7eb22c6595`. Page bytes are not redistributed.

### Selection purpose

Official logical state graph tests parallel branches and join semantics without confusing workflow states with cloud resources.

### Authored source sections

- Parallel processing

### Core components

- One Step Functions state-machine/orchestration intent
- Three distinct Lambda task intents: Create Thumbnail, Add Watermark, Extract Metadata

### Material directed relationships

- Start enters the Process Image Parallel state.
- That Parallel state invokes three distinct Lambda task branches concurrently.
- All three branches complete before the parallel join allows End; branch start/end markers are control states.

### Containment / boundaries

- The three Lambda task nodes are separate branches of a single logical Step Functions Parallel state.
- The image supplies no VPC/subnet/AZ or bucket boundary.

### Material cardinality

- One state-machine intent with exactly three parallel task branches and three distinct Lambda task responsibilities.
- Start, End and branch dots are control nodes, not extra functions/services.

### Forbidden interpretations

- Serial thumbnail -> watermark -> metadata execution.
- Substituting a Map iteration, Lambda-only orchestrator or three independent state machines for the depicted Parallel/fan-out/join.
- Inventing S3, queues, Glue or other input/output services because the workflow processes images.
- Creating AWS resources for Start/End/control markers.

### Terraform resource intent

- Step Functions state machine with a Parallel state and three Lambda invocation task branches; role/invoke capability must be supplied.
- Three Lambda function intents or declared existing function ARN references, preserving task semantics; packages/handlers are user inputs.

### Actors / logical / external concepts

- Process Image, Start, End, branch dots and workflow arrows are logical states/control flow.
- The input image payload is workflow data, not evidence of an S3 bucket.

### Legitimate unknown inputs

- Lambda code packages, handlers/runtime, role/function ARNs, account/region, concrete task payloads and optional timeout/error policies.

### Remaining realism limitations

- This official diagram is intentionally clean and abstract, with no network or deployment-specific values.
- Other diagrams on the same page describe maps/sequences; only the exact pinned parallel_workflow.png is selected.

### Explicit truth-review notes

- The page explains simultaneous execution and waiting for all branches. No exact ASL text or resource naming equality is required.

## Case E — Containerized scalable web application

[Official authored source](https://docs.aws.amazon.com/solutions/building-a-containerized-and-scalable-web-application-on-aws/) · [Exact input image](https://docs.aws.amazon.com/images/solutions/building-a-containerized-and-scalable-web-application-on-aws/images/building-a-containerized-and-scalable-web-application-on-aws-1.png)

![Case E official input](https://docs.aws.amazon.com/images/solutions/building-a-containerized-and-scalable-web-application-on-aws/images/building-a-containerized-and-scalable-web-application-on-aws-1.png)

Image SHA-256: `2ba2f1d238026058bde92c21f88ba9263dcd9e5341606ad80155239564c4ccaa`. Original 2968 × 1600; 177632 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.992758+09:00`; image accessed `2026-10-07T14:59:55.421036+09:00`.
Source-page snapshot SHA-256: `47e518ed1982b805ac074bb4b65cf04d440d63ae42cc2e42b9549d1c46c80d44`. Page bytes are not redistributed.

### Selection purpose

High service diversity, two AZs, static/dynamic split, dense/crossing arrows and operational side paths stress architecture fidelity.

### Authored source sections

- How it works
- Steps 1 through 10

### Core components

- Route 53 DNS
- Cognito authentication
- CloudFront content delivery
- S3 static-content origin
- API Gateway dynamic-request front door
- One logical Application Load Balancer
- ECS on Fargate application capacity across two AZs
- DynamoDB data access
- ECR container-image supply
- CloudWatch monitoring intent
- VPC with private application subnets and conceptual private data-tier boundaries across two AZs

### Material directed relationships

- Client resolves DNS via Route 53, authenticates through Cognito and reaches CloudFront content delivery; these numbered logical steps do not make DNS an application proxy.
- CloudFront delivers static content from S3 and routes dynamic requests through API Gateway -> ALB -> ECS/Fargate application capacity.
- ECS/Fargate application work reads/writes the DynamoDB data tier.
- ECS/Fargate pulls application container images from ECR.
- Application/load-balancer/data-tier operational telemetry reaches CloudWatch; monitoring arrows are not user-request routing.
- Static origin access, API/backend routing and task/data permissions must be supplied or explicitly parameterized rather than silently omitted.

### Containment / boundaries

- One VPC shows two AZ groups, each with a private application subnet and a labeled private database subnet/data-tier boundary.
- Fargate tasks are private application capacity distributed across AZs.
- The graphic places DynamoDB icons inside private database-subnet boxes, but DynamoDB is a regional managed service, not subnet-deployable. Treat these as conceptual data-tier boundaries; never require impossible DynamoDB subnet_ids.
- The authored step 6 calls the ALB internet-facing while the drawing shows only private subnet boxes. Preserve that front-door intent and make required public ALB subnet/route capability explicit via supplied network references or minimal supported plumbing; do not invent a unrelated stack.

### Material cardinality

- Two AZs and one private application tier per AZ; one logical ALB and one logical cross-AZ Fargate application-service intent.
- Repeated ECS/DynamoDB icons do not by themselves require two clusters/services or two DynamoDB tables.
- Both static and dynamic content branches plus ECR and CloudWatch intent must remain distinguishable.

### Forbidden interpretations

- Replacing ECS/Fargate with EKS, EC2, Lambda or another compute service, or DynamoDB with RDS.
- Omitting the static S3/CloudFront branch or the dynamic API Gateway/ALB/Fargate chain.
- Treating ECR image supply or CloudWatch telemetry as serial user-request processing.
- Literal subnet placement of DynamoDB or mandatory duplicate tables/clusters solely from repeated AZ icons.
- Creating a second unrelated cloud architecture to fill unspecified network/account values.

### Terraform resource intent

- Route 53/domain, Cognito/auth boundary, CloudFront distribution with static/dynamic origin behaviors and S3 access capability, API Gateway integrations and ALB/listener/targets.
- ECS/Fargate task/service, image supply via ECR, VPC/private application placement, security/role intent and DynamoDB data access.
- CloudWatch monitoring/log capability and conceptual data-tier segmentation; a capability may use grounded declared external resources rather than every icon becoming a new block.
- Network plumbing needed for the authored internet-facing ALB may be declared via external public-subnet variables/references; the diagram does not specify CIDRs or route implementation.

### Actors / logical / external concepts

- Client/web application, numbered steps, DNS flow and conceptual tier boxes are actors/logical flows.
- Cognito account/callbacks, existing domain/certificate, container image, VPC/subnets and roles may be external inputs.
- DynamoDB is not physically created inside a subnet and repeated icons do not establish table count.

### Legitimate unknown inputs

- Domains/hosted zones/ACM ARNs, Cognito callback/logout/IdP configuration, container image/tag, application ports and health checks, IAM/account/region identifiers, VPC/subnet IDs/CIDRs, DynamoDB key schema and secrets.

### Remaining realism limitations

- The official illustration abstracts DynamoDB placement and omits public subnet detail despite an internet-facing ALB description; these are explicit human truth-review caveats.
- Ten numbered AWS service families make the sample complex but not a universal image generalization claim.
- Exact authentication/integration/network implementation is not supplied; a draft may expose coherent external inputs while preserving depicted intent.

### Explicit truth-review notes

- Explicitly approve conceptual DynamoDB containment and the page-text internet-facing ALB reconciliation before freeze. This is proposed truth, not self-approved product architecture.
- Allow equivalent valid integrations/authorization. Do not force invented concrete IDs or a single exact bucket-policy text.
