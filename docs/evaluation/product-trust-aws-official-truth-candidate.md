# AWS official acceptance set — truth candidate revision 2

**CANDIDATE NOT APPROVED.** `truthFrozen=false`, `executionAuthorized=false`, model-under-test runs **0**. Stop at `AWS_OFFICIAL_ACCEPTANCE_SET_TRUTH_FREEZE_AND_V4_AMENDMENT_REVIEW`.

Human-authorized correction for [review 6032307527](https://github.com/siamese-lang/terraformers-platform/pull/253#issuecomment-6032307527) addresses only truth authority. All five exact images, URLs, hashes, media/dimensions and access metadata are unchanged. [Candidate revision 1](../../evaluation/terraformers-aws-official-v1/superseded/candidate-01-identity.json), identity `fad362718ea64050aaa2f0b37dd8225e58ad68da9fb11d143295e32b7c404a10`, is preserved as superseded pre-freeze evidence with its exact manifest and human-review bytes. No outcome/model informed these edits.

## Authority and scoring boundary

Terraformers receives **only image bytes**. Mandatory semantic component/relationship/containment/cardinality/forbidden scoring uses **image_observable_truth** only. **documentation_context** may identify/disambiguate visible symbols/logical repeats, but cannot add a hidden page-only semantic label. **draft_technical_closure** checks coherence only for relationships/resources actually chosen or created by the generated draft. A page-only example alone never triggers closure or a missing image-fact failure.

Visible topology remains mandatory. Unknown account/domain/certificate/role/IdP/network/code values can be declared variables, coherent external references or editable valid inputs under **REVIEWABLE_IAC_DRAFT**. No fabricated real identifiers. Support wiring/authorization cannot be silently omitted for a draft-chosen relationship, but no exact page listener/policy/route/health setting is a vision requirement. Real Dockerfile-pinned Terraform 1.8.5 / AWS 5.100.0 init/validate must pass; no AWS plan/apply/deployment oracle. Equivalent valid grounded mechanisms remain acceptable.

The ten v4 scoring dimensions and FALSE_TRUSTED_SUCCESS=0 are preserved, with these authorities distinguished. Technical CLI PASS does not prove semantic fidelity, and a documentation-only omission does not become false-trusted semantic failure. No exact text/resource-name matching or model judge.

## Human approval still required

Freeze the exact [candidate identity](../../evaluation/terraformers-aws-official-v1/candidate-identity.json), current manifest and this document only after independent re-review and explicit USER approval. The archive mapping in the current manifest binds revision 1 original paths to preserved files without rewriting its identity. Candidate snapshots retain their pre-approval markers; any later approval must be recorded outside pinned bytes. No self-freeze/merge or PT-6R2/live execution.

Exact images are linked below, not redistributed. Future fetch must match the unchanged raw SHA/media/dimensions before upload; no redraw/crop/restyle/substitute.

## Case A — Serverless web application

[Official page/context](https://docs.aws.amazon.com/whitepapers/latest/serverless-multi-tier-architectures-api-gateway-lambda/web-application.html) · [Exact input image](https://docs.aws.amazon.com/images/whitepapers/latest/serverless-multi-tier-architectures-api-gateway-lambda/images/web-application.png)

![Case A official input](https://docs.aws.amazon.com/images/whitepapers/latest/serverless-multi-tier-architectures-api-gateway-lambda/images/web-application.png)

Raw SHA-256 `0a6a59273dc5a70ab10415328a53427291c2b08c25a94f088bb874a607946007`; 974 × 591; 172040 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.989581+09:00`; image accessed `2026-10-07T14:59:55.416593+09:00`. Page snapshot SHA-256 `c5fd8fa0a79046beaa9c97d043b24f85fb50a690c591c6648627b618ee433f28`.

Medium-complexity web/serverless graph; authentication and deployment-specific domains, certificates and identity inputs test the draft boundary.

### image_observable_truth

**Visible components/service families**

- Route 53, labeled Amazon Route 53 and domain-name mapping.
- CloudFront, labeled Amazon CloudFront, connected to the client.
- API Gateway, labeled Amazon API Gateway.
- Cognito, labeled Amazon Cognito; the client caption includes user-pool or third-party identity login/token.
- Three Lambda intents, labeled /tickets, /shows and /info.
- DynamoDB, labeled Amazon DynamoDB.
- ACM, labeled AWS Certificate Manager with Enable SSL/TLS via custom certificate caption.
- A role symbol/name at each Lambda: lambda-ticket-role, lambda-show-role and lambda-info-role; specific policy statements are not displayed.

**Visible/necessarily encoded directed relationships**

- Client connects to the Route 53 domain-mapping path and CloudFront; the diagram also connects API Gateway to domain/certificate capabilities.
- Client exchanges with Cognito and sends the depicted HTTPS/token request to API Gateway; API Gateway connects to Cognito for identity verification.
- API Gateway branches to the /tickets, /shows and /info Lambda intents.
- Each Lambda has a bidirectional connection to DynamoDB, with a Retrieve appropriate DynamoDB information caption.
- The API/domain branch connects to ACM and its depicted SSL/TLS capability; no particular certificate ARN or DNS-record implementation is shown.

**Visible topology associations**

- Each of the three Lambda nodes is associated with its own depicted role symbol/name. This is role intent, not an image of exact IAM permissions.

**Meaningful containment/boundaries**

- Client/browser is outside the AWS Cloud boundary; the labeled services and three Lambda branches are inside it.
- No VPC/subnet/AZ boundary is drawn.

**Material cardinality**

- Three distinct API/function branches and their /tickets, /shows and /info responsibilities must remain distinguishable.
- Three role associations are depicted; illustrative role/resource names need not match generated identifiers.
- A single DynamoDB service symbol does not specify table count; one logical CloudFront/API/identity/certificate service capability is shown.

**Forbidden depicted-architecture misreadings**

- Replacing the Lambda/DynamoDB architecture with EC2, containers or RDS.
- Converting identity verification into a depicted direct Cognito-to-Lambda invocation, or DNS/certificate support into serial application compute.
- Omitting the visible Cognito/authentication intent or collapsing the three distinct API/Lambda branches.
- Requiring a depicted S3 bucket: there is no S3 icon in this image.
- Inventing VPC/NAT placement or a CloudFront-to-API edge that is not drawn.

**Visible service/resource intent**

- Preserve visible CloudFront, API/Cognito, three Lambda/DynamoDB, Route 53/ACM and per-function role intent. Capabilities may be generated or coherently represented by declared external inputs/references.

**Actors/logical/external concepts**

- Client/browser, illustrative URLs and path labels are actors/logical flows, not mandatory new cloud resources.
- The visible third-party identity-provider alternative and user-supplied domains/certificates/roles need not be provisioned anew.

### documentation_context

Context/disambiguation only; **not additional mandatory semantic truth**.

**Disambiguation**

- Authored tier descriptions identify presentation/logic/data roles; they do not add unseen services or edges.

**Page-only detail**

- The page describes React/static frontend delivery through CloudFront. Static implementation details are not additional image requirements.
- Exact domain/DNS record configuration, certificate issuance details, Lambda IAM policy actions/scopes, table key schema and code/runtime settings are not determined by the image.

Authored context sections: Architecture overview, Presentation tier, Logic tier, Data tier.

### draft_technical_closure

Only the relevant capabilities/relationships actually chosen or created by the generated draft; not the existence of a page-only example.

- If the draft realizes API Gateway-to-Lambda routes, supply coherent method/integration/permission wiring for the chosen implementation, not just unrelated resource blocks.
- If it realizes Lambda-to-DynamoDB access, supply appropriate authorization or a coherent declared existing-role boundary; no exact policy text/action list is an image label.
- If it configures a custom-domain/TLS path, use valid domain/certificate/zone references or declared inputs; do not require page-only issuance/DNS mechanics in semantic scoring.
- If it creates CloudFront delivery, provide a coherent origin/reference and its necessary access wiring; no newly created S3 origin is forced by the image.

Unknown/personalized inputs:

- Domains/hosted zones/certificate and role ARNs, IdP/client/callback/logout settings, account/region, Lambda code/handler/runtime, data-table particulars and origin identifiers may remain declared variables/coherent references/editable inputs.

Common real CLI/provider validity and REVIEWABLE_IAC_DRAFT rules above apply.

### Remaining limitations

- The authored page warns that the whitepaper is historical; the image is a pinned sample, not current best-practice authority.
- Clean labels/icons and unavailable personalized implementation values limit generalization.

### Audit of every prior requirement

Source IDs identify exact fields/items in the [superseded manifest](../../evaluation/terraformers-aws-official-v1/superseded/candidate-01-manifest.json). Mixed assertions are split; image-only items remain mandatory. Targets identify current authority/field/item, not production code.

| Prior source item | Current authority destinations |
| --- | --- |
| coreComponents:1 | `image_observable_truth/components:1` |
| coreComponents:2 | `image_observable_truth/components:2` |
| coreComponents:3 | `image_observable_truth/components:3` |
| coreComponents:4 | `image_observable_truth/components:4` |
| coreComponents:5 | `image_observable_truth/components:5` |
| coreComponents:6 | `image_observable_truth/components:6` |
| coreComponents:7 | `image_observable_truth/components:7`; `draft_technical_closure/conditional_requirements:3` |
| coreComponents:8 | `image_observable_truth/components:8`; `image_observable_truth/topology_associations:1`; `draft_technical_closure/conditional_requirements:2` |
| directedRelationships:1 | `image_observable_truth/directed_relationships:1`; `documentation_context/page_only_details:1`; `draft_technical_closure/conditional_requirements:4` |
| directedRelationships:2 | `image_observable_truth/directed_relationships:2` |
| directedRelationships:3 | `image_observable_truth/directed_relationships:3`; `draft_technical_closure/conditional_requirements:1` |
| directedRelationships:4 | `image_observable_truth/directed_relationships:4`; `draft_technical_closure/conditional_requirements:2` |
| directedRelationships:5 | `image_observable_truth/directed_relationships:5`; `draft_technical_closure/conditional_requirements:3` |
| containment:1 | `image_observable_truth/containment:1`; `image_observable_truth/actors_logical_external:2` |
| containment:2 | `image_observable_truth/containment:2` |
| materialCardinality:1 | `image_observable_truth/material_cardinality:1` |
| materialCardinality:2 | `image_observable_truth/material_cardinality:3` |
| materialCardinality:3 | `image_observable_truth/material_cardinality:3` |
| forbiddenInterpretations:1 | `image_observable_truth/forbidden_misreadings:1` |
| forbiddenInterpretations:2 | `image_observable_truth/forbidden_misreadings:2` |
| forbiddenInterpretations:3 | `image_observable_truth/forbidden_misreadings:3` |
| forbiddenInterpretations:4 | `image_observable_truth/forbidden_misreadings:4` |
| forbiddenInterpretations:5 | `image_observable_truth/forbidden_misreadings:5` |
| resourceIntent:1 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:1`; `draft_technical_closure/conditional_requirements:4` |
| resourceIntent:2 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:2`; `draft_technical_closure/unknown_inputs:1` |
| resourceIntent:3 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:3`; `draft_technical_closure/unknown_inputs:1` |
| notRequiredAsNewResources:1 | `image_observable_truth/actors_logical_external:1` |
| notRequiredAsNewResources:2 | `image_observable_truth/actors_logical_external:2`; `draft_technical_closure/unknown_inputs:1` |
| unknownInputs:1 | `draft_technical_closure/unknown_inputs:1` |

## Case B — Private multi-AZ web fleet

[Official page/context](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-example-private-subnets-nat.html) · [Exact input image](https://docs.aws.amazon.com/images/vpc/latest/userguide/images/vpc-example-private-subnets.png)

![Case B official input](https://docs.aws.amazon.com/images/vpc/latest/userguide/images/vpc-example-private-subnets.png)

Raw SHA-256 `d14c4ba606cd3edf26abc93721577293d1378bc3f3903a0d678af020295b162b`; 611 × 481; 39193 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.990564+09:00`; image accessed `2026-10-07T14:59:55.417518+09:00`. Page snapshot SHA-256 `f2bcffa48368b187e496e230ef30dd8a38fbca5d45504cd23f1f3442b923dffe`.

Nested VPC/AZ/subnet grouping, repeated AZ symbols, NAT cardinality and distinct inbound/egress routes test network semantics.

### image_observable_truth

**Visible components/service families**

- One VPC boundary and its edge gateway symbol (official context identifies the internet gateway).
- Two labeled Availability Zone boundaries.
- A labeled public and private subnet in each AZ.
- A labeled NAT gateway in each public subnet.
- One Application Load Balancer grouping with a node symbol in each public subnet.
- One Auto Scaling group across the private server fleet, with server symbols in both AZs.
- A labeled Security group boundary around private server capacity.
- A labeled S3 gateway symbol at the VPC boundary (S3 gateway-endpoint intent).

**Visible/necessarily encoded directed relationships**

No directed packet-flow arrows are drawn. Preserve the topology associations/boundaries below; do not invent page-derived direction as a mandatory image label.

**Visible topology associations**

- The shared ALB grouping spans the public subnets, and the ASG/security-group grouping spans private server capacity in both AZs.
- Each NAT gateway belongs to its AZ public subnet; the VPC edge gateway and S3 gateway are visible connectivity capabilities.
- The image has no packet-flow arrows. Do not turn page-only route-table entries, health checks, listener ports or ALB-security-group rules into missing image relationships.

**Meaningful containment/boundaries**

- One VPC spans two AZ groups, each containing one public subnet with NAT/ALB node and one private subnet with server capacity.
- ALB nodes are public; the ASG and labeled server security-group boundary span private capacity.
- Regional service symbols above the VPC are external service context, not instances deployed in its subnets.

**Material cardinality**

- Two AZs, two public and two private subnets, two NAT gateways (one in each public subnet).
- One logical ALB grouping and one logical cross-AZ ASG; repeated ALB node symbols do not require two load balancers.
- Server capacity is represented in both AZs. The two representative symbols do not freeze an exact new EC2 desired-capacity count.

**Forbidden depicted-architecture misreadings**

- Moving the visible private application fleet into public subnets.
- Collapsing the two-AZ/subnet/NAT topology to a single AZ or one NAT gateway.
- Interpreting the repeated ALB nodes as two independent ALBs or the shared ASG as two groups.
- Omitting the visible S3 gateway-endpoint intent.
- Turning unlabeled regional context icons into mandatory newly created VPC resources.

**Visible service/resource intent**

- Preserve VPC, two-AZ public/private tiers, two NAT capabilities, edge/S3 gateways, one logical ALB/ASG and server/security-group boundary; no particular route entries or listener rules are image labels.

**Actors/logical/external concepts**

- Region/AZ names and repeated capacity/service symbols are context/logical grouping; existing networks and service endpoints may be declared external boundaries.

### documentation_context

Context/disambiguation only; **not additional mandatory semantic truth**.

**Disambiguation**

- The page identifies the VPC edge gateway as an internet gateway and the S3 gateway as a gateway endpoint.
- Its ALB-node/ASG description disambiguates repeated symbols as shared logical services, not separate resources.

**Page-only detail**

- The page describes internet ingress through the ALB and ALB forwarding/health checks to private EC2 targets; the image does not draw packet direction/protocol/port details.
- Public 0.0.0.0/0-to-IGW routes, private IPv4 default routes through same-AZ NAT, S3 prefix routes/route-table associations and ALB-security-group ingress rules are page-only implementation detail.
- The page allows IPv4-only or optional dual-stack/egress-only IPv6 variants; it does not make an unseen IPv6 gateway an image requirement.

Authored context sections: Overview, Routing, Security.

### draft_technical_closure

Only when the generated draft chooses the corresponding network/listener/target/egress relationship; do not infer a missing image label from AWS page instructions.

- Created NAT gateways need valid provider-required placement/address inputs; if the draft configures private internet egress, its NAT/IGW/routes must be coherent with its chosen subnets. Exact page default routes are not mandatory vision facts.
- If the draft connects the ALB to server capacity, listener/target-group/ASG and relevant security rules must coherently realize that chosen connection, with ports/health settings declared or editable.
- If the draft realizes the S3 gateway endpoint, its VPC/service/route associations must be valid; existing endpoint/network references are acceptable.
- A chosen IPv6/public routing mode must be technically coherent; no IPv6 resource or exact listener/health-check rule is required merely because the page discusses it.

Unknown/personalized inputs:

- Region/AZ IDs, CIDRs, VPC/subnet/role identifiers, AMI/instance type/capacity, user data, ports and health-check settings may be variables/coherent external references/editable inputs.

Common real CLI/provider validity and REVIEWABLE_IAC_DRAFT rules above apply.

### Remaining limitations

- This image encodes topology through containment and repeated symbols rather than directed routing arrows; only those visible associations are semantic truth.
- The service/capacity symbols are illustrative, not a fully parameterized deployment spec.

### Audit of every prior requirement

Source IDs identify exact fields/items in the [superseded manifest](../../evaluation/terraformers-aws-official-v1/superseded/candidate-01-manifest.json). Mixed assertions are split; image-only items remain mandatory. Targets identify current authority/field/item, not production code.

| Prior source item | Current authority destinations |
| --- | --- |
| coreComponents:1 | `image_observable_truth/components:1`; `documentation_context/icon_or_logical_disambiguation:1` |
| coreComponents:2 | `image_observable_truth/components:2` |
| coreComponents:3 | `image_observable_truth/components:3` |
| coreComponents:4 | `image_observable_truth/components:4` |
| coreComponents:5 | `image_observable_truth/components:5`; `documentation_context/icon_or_logical_disambiguation:2` |
| coreComponents:6 | `image_observable_truth/components:6` |
| coreComponents:7 | `image_observable_truth/components:7` |
| coreComponents:8 | `image_observable_truth/components:8`; `documentation_context/icon_or_logical_disambiguation:1` |
| directedRelationships:1 | `image_observable_truth/topology_associations:1`; `documentation_context/page_only_details:1`; `draft_technical_closure/conditional_requirements:2` |
| directedRelationships:2 | `documentation_context/page_only_details:2`; `draft_technical_closure/conditional_requirements:1` |
| directedRelationships:3 | `documentation_context/page_only_details:2`; `draft_technical_closure/conditional_requirements:1` |
| directedRelationships:4 | `image_observable_truth/topology_associations:2`; `documentation_context/page_only_details:2`; `draft_technical_closure/conditional_requirements:3` |
| directedRelationships:5 | `documentation_context/page_only_details:2`; `draft_technical_closure/conditional_requirements:2` |
| containment:1 | `image_observable_truth/containment:1` |
| containment:2 | `image_observable_truth/containment:2` |
| containment:3 | `image_observable_truth/containment:3` |
| materialCardinality:1 | `image_observable_truth/material_cardinality:1` |
| materialCardinality:2 | `image_observable_truth/material_cardinality:2`; `documentation_context/icon_or_logical_disambiguation:2` |
| materialCardinality:3 | `image_observable_truth/material_cardinality:3` |
| forbiddenInterpretations:1 | `image_observable_truth/forbidden_misreadings:1`; `documentation_context/page_only_details:2`; `draft_technical_closure/conditional_requirements:1` |
| forbiddenInterpretations:2 | `image_observable_truth/forbidden_misreadings:2` |
| forbiddenInterpretations:3 | `image_observable_truth/forbidden_misreadings:3` |
| forbiddenInterpretations:4 | `image_observable_truth/forbidden_misreadings:4`; `documentation_context/page_only_details:2`; `draft_technical_closure/conditional_requirements:3` |
| forbiddenInterpretations:5 | `image_observable_truth/forbidden_misreadings:5` |
| forbiddenInterpretations:6 | `documentation_context/page_only_details:3`; `draft_technical_closure/conditional_requirements:4` |
| resourceIntent:1 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:1` |
| resourceIntent:2 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:2` |
| resourceIntent:3 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:3` |
| notRequiredAsNewResources:1 | `image_observable_truth/actors_logical_external:1` |
| notRequiredAsNewResources:2 | `image_observable_truth/containment:3`; `image_observable_truth/actors_logical_external:1` |
| unknownInputs:1 | `draft_technical_closure/unknown_inputs:1` |

## Case C — Cross-region S3 event notification

[Official page/context](https://docs.aws.amazon.com/prescriptive-guidance/latest/patterns/subscribe-a-lambda-function-to-event-notifications-from-s3-buckets-in-different-aws-regions.html) · [Exact input image](https://docs.aws.amazon.com/images/prescriptive-guidance/latest/patterns/images/pattern-img/cf6c1804-8c41-46f1-9f17-ff361708c595/images/760cf4c0-0cb3-48d1-92ae-1cf0fa8ae076.png)

![Case C official input](https://docs.aws.amazon.com/images/prescriptive-guidance/latest/patterns/images/pattern-img/cf6c1804-8c41-46f1-9f17-ff361708c595/images/760cf4c0-0cb3-48d1-92ae-1cf0fa8ae076.png)

Raw SHA-256 `ace1b957a920f516f2bd88cfa779cd8b899e4bd40b3cdf562f478d141c892a8d`; 1280 × 720; 87687 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.991218+09:00`; image accessed `2026-10-07T14:59:55.418185+09:00`. Page snapshot SHA-256 `fc5b2172d134bead6038aa342d6fe90bf2de4af031bd5b2c6d2362572d3c0b1c`.

Regional grouping and many-to-one notification flow test service-specific direction and cross-region constraints.

### image_observable_truth

**Visible components/service families**

- S3 bucket-source groups in Region 1, Region 2, Region 3 and Central Region.
- One labeled SNS topic in each of those four region groups.
- One labeled SQS queue in Central Region.
- One labeled Lambda function event handler in Central Region.

**Visible/necessarily encoded directed relationships**

- Each region’s S3 source group has arrows to its own SNS topic.
- The three remote SNS branches and the central SNS branch fan into the single central SQS queue.
- The central SQS queue has a directed edge to the central Lambda event handler; the image does not specify polling/event-source-mapping implementation.

**Visible topology associations**

- Notification source groups and their SNS topics remain region-local; queue/consumer fan-in is central.

**Meaningful containment/boundaries**

- Three remote region boxes plus a Central Region box; Central Region contains its S3/SNS source group and the SQS/Lambda consumer path.

**Material cardinality**

- Four source-region groups/four SNS topic intents, one central SQS and one central Lambda handler.
- Four bucket symbols are visible in each region group (sixteen symbols total). Preserve plural bucket sources in all four groups; this does not require sixteen newly provisioned buckets.

**Forbidden depicted-architecture misreadings**

- Bypassing the depicted SNS/SQS bridge with direct cross-region S3-to-Lambda edges.
- Replacing the central SQS path with direct SNS-to-Lambda delivery.
- One global SNS topic, independent queues/functions per source region, or omission of the central source group.
- Interpreting the depicted event-handler notification flow as object-data replication rather than event delivery.

**Visible service/resource intent**

- Regional S3 notification/SNS source intent feeding one central SQS/Lambda consumer intent; existing bucket/topic/queue/function inputs may supply capabilities without new resource creation per symbol.

**Actors/logical/external concepts**

- Region boxes and numbered stage badges are logical grouping; bucket sets may be supplied external resources.

### documentation_context

Context/disambiguation only; **not additional mandatory semantic truth**.

**Disambiguation**

- The authored pattern identifies event-notification fan-in and starts from existing S3 buckets; repeated bucket symbols represent multiple source buckets, not a demand for sixteen new creations.

**Page-only detail**

- Lambda polling via SQS event-source mapping, SNS subscriptions, topic publishing policies, queue subscription access policies and consumer IAM configuration are implementation context, not additional image relationships.
- Exact region/account ARNs, event filters, queue operational parameters and example policy statements are not visible image labels.

Authored context sections: Summary, Architecture, Tools, Epics.

### draft_technical_closure

Only when the generated draft creates/configures the depicted S3/SNS/SQS/Lambda connections; permission mechanics are not image-extraction labels.

- A chosen S3-to-SNS notification implementation needs region-coherent notification targets and valid publishing authorization or explicit existing-resource boundaries.
- A chosen SNS-to-SQS connection needs a coherent subscription/delivery and queue-access capability; accept equivalent valid supplied permissions rather than exact page policy text.
- A chosen SQS/Lambda consumer connection needs coherent event-source/consumer wiring and authorization; page-only polling mechanics are not a missing image relationship.
- Provider aliases/region/account references must make the chosen cross-region setup coherent; do not fabricate account-specific ARNs or require newly created buckets for all symbols.

Unknown/personalized inputs:

- Actual region names, external bucket lists/names/event filters, topic/queue/function and IAM ARNs, consumer code/runtime and operational queue parameters may remain declared inputs/references.

Common real CLI/provider validity and REVIEWABLE_IAC_DRAFT rules above apply.

### Remaining limitations

- Illustrative repeated buckets do not freeze exact new resource counts.
- Policy/account settings are absent; CLI validity cannot prove effective deployed cross-account access.

### Audit of every prior requirement

Source IDs identify exact fields/items in the [superseded manifest](../../evaluation/terraformers-aws-official-v1/superseded/candidate-01-manifest.json). Mixed assertions are split; image-only items remain mandatory. Targets identify current authority/field/item, not production code.

| Prior source item | Current authority destinations |
| --- | --- |
| coreComponents:1 | `image_observable_truth/components:1` |
| coreComponents:2 | `image_observable_truth/components:2` |
| coreComponents:3 | `image_observable_truth/components:3` |
| coreComponents:4 | `image_observable_truth/components:4` |
| directedRelationships:1 | `image_observable_truth/directed_relationships:1`; `draft_technical_closure/conditional_requirements:1` |
| directedRelationships:2 | `image_observable_truth/directed_relationships:2`; `draft_technical_closure/conditional_requirements:2` |
| directedRelationships:3 | `image_observable_truth/directed_relationships:3`; `documentation_context/page_only_details:1`; `draft_technical_closure/conditional_requirements:3` |
| directedRelationships:4 | `documentation_context/page_only_details:1`; `draft_technical_closure/conditional_requirements:1`; `draft_technical_closure/conditional_requirements:2`; `draft_technical_closure/conditional_requirements:3` |
| containment:1 | `image_observable_truth/containment:1` |
| containment:2 | `image_observable_truth/topology_associations:1` |
| materialCardinality:1 | `image_observable_truth/material_cardinality:1` |
| materialCardinality:2 | `image_observable_truth/material_cardinality:2`; `documentation_context/icon_or_logical_disambiguation:1` |
| forbiddenInterpretations:1 | `image_observable_truth/forbidden_misreadings:1` |
| forbiddenInterpretations:2 | `image_observable_truth/forbidden_misreadings:2` |
| forbiddenInterpretations:3 | `image_observable_truth/forbidden_misreadings:3` |
| forbiddenInterpretations:4 | `image_observable_truth/forbidden_misreadings:4`; `documentation_context/icon_or_logical_disambiguation:1` |
| resourceIntent:1 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:1`; `draft_technical_closure/conditional_requirements:4` |
| resourceIntent:2 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:2`; `draft_technical_closure/conditional_requirements:3` |
| resourceIntent:3 | `image_observable_truth/resource_intent:1`; `image_observable_truth/actors_logical_external:1`; `draft_technical_closure/conditional_requirements:4` |
| notRequiredAsNewResources:1 | `image_observable_truth/actors_logical_external:1` |
| notRequiredAsNewResources:2 | `image_observable_truth/actors_logical_external:1`; `documentation_context/icon_or_logical_disambiguation:1` |
| unknownInputs:1 | `draft_technical_closure/unknown_inputs:1` |

## Case D — Parallel image workflow

[Official page/context](https://docs.aws.amazon.com/lambda/latest/dg/with-step-functions.html) · [Exact input image](https://docs.aws.amazon.com/images/lambda/latest/dg/images/parallel_workflow.png)

![Case D official input](https://docs.aws.amazon.com/images/lambda/latest/dg/images/parallel_workflow.png)

Raw SHA-256 `9439b99a3e99155ca9e943b305cf0e4ad738d6e81cb8ae853b1e8b82013ae59b`; 824 × 396; 34319 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.992061+09:00`; image accessed `2026-10-07T14:59:55.419425+09:00`. Page snapshot SHA-256 `e67cb8c4e7f648767f89003f6a0ee909c38c59930db8720c5b85bf7eb22c6595`.

Official logical state graph tests parallel branches and join semantics without confusing workflow states with cloud resources.

### image_observable_truth

**Visible components/service families**

- One Process Image Parallel state/orchestration graph, with explicit Parallel state label/symbol.
- Three distinct Lambda: Invoke tasks labeled Create Thumbnail, Add Watermark and Extract Metadata.

**Visible/necessarily encoded directed relationships**

- Start leads to the Process Image Parallel state.
- The Parallel state fans out to the three distinct Lambda branches.
- Each branch reaches its branch-end marker, and the shared enclosing Parallel state joins before End.

**Visible topology associations**

- The three distinct task branches belong to one parallel orchestration, not separate state machines.

**Meaningful containment/boundaries**

- All three task branches are inside the one Parallel state boundary; no VPC/subnet/AZ or bucket boundary is shown.

**Material cardinality**

- One logical parallel orchestration, exactly three distinct Lambda branch responsibilities and one final join.
- Start/End and three branch dots are control markers, not additional Lambda/services.

**Forbidden depicted-architecture misreadings**

- Serial thumbnail -> watermark -> metadata execution.
- Substituting Map iteration, a Lambda-only orchestrator or three independent state machines for the visible Parallel fan-out/join.
- Inventing S3, queues, Glue or other services merely because task labels describe image processing.
- Provisioning cloud resources for Start/End/control dots.

**Visible service/resource intent**

- Step Functions-style parallel state-machine intent with three distinct Lambda invocation branches and preserved join; existing function/state-machine references may be declared.

**Actors/logical/external concepts**

- Process Image/Start/End/dots/arrows are logical states/control flow; image-processing payload is data, not a depicted bucket.

### documentation_context

Context/disambiguation only; **not additional mandatory semantic truth**.

**Disambiguation**

- The page identifies this graph as Step Functions parallel processing and explains concurrent branches followed by completion/join; those semantics are already encoded by the explicit Parallel label/graph.

**Page-only detail**

- Ordered result arrays, timeout/error policies and detailed ASL/task input syntax are not extra image truth. Other Map/sequence diagrams on the page are not the selected input.

Authored context sections: Parallel processing.

### draft_technical_closure

When the draft implements the depicted state-machine/Lambda branch relationship; no page-only timeout or payload setting is mandatory image truth.

- A generated state-machine definition must realize Parallel branches, valid task references and the join without serializing the depicted tasks.
- The chosen Lambda invocation integration needs valid function/role references and invocation authorization or explicit external capability boundaries; no exact page ASL/policy text is required.

Unknown/personalized inputs:

- Function/role ARNs, account/region, Lambda packages/handlers/runtime, payload and optional timeout/error choices may remain declared inputs.

Common real CLI/provider validity and REVIEWABLE_IAC_DRAFT rules above apply.

### Remaining limitations

- The graph is clean and abstract; it omits networking and deploy-time values.
- Exact ASL text/resource names are not observable requirements.

### Audit of every prior requirement

Source IDs identify exact fields/items in the [superseded manifest](../../evaluation/terraformers-aws-official-v1/superseded/candidate-01-manifest.json). Mixed assertions are split; image-only items remain mandatory. Targets identify current authority/field/item, not production code.

| Prior source item | Current authority destinations |
| --- | --- |
| coreComponents:1 | `image_observable_truth/components:1`; `documentation_context/icon_or_logical_disambiguation:1` |
| coreComponents:2 | `image_observable_truth/components:2` |
| directedRelationships:1 | `image_observable_truth/directed_relationships:1` |
| directedRelationships:2 | `image_observable_truth/directed_relationships:2` |
| directedRelationships:3 | `image_observable_truth/directed_relationships:3`; `documentation_context/icon_or_logical_disambiguation:1` |
| containment:1 | `image_observable_truth/containment:1`; `image_observable_truth/topology_associations:1` |
| containment:2 | `image_observable_truth/containment:1` |
| materialCardinality:1 | `image_observable_truth/material_cardinality:1` |
| materialCardinality:2 | `image_observable_truth/material_cardinality:2` |
| forbiddenInterpretations:1 | `image_observable_truth/forbidden_misreadings:1` |
| forbiddenInterpretations:2 | `image_observable_truth/forbidden_misreadings:2` |
| forbiddenInterpretations:3 | `image_observable_truth/forbidden_misreadings:3` |
| forbiddenInterpretations:4 | `image_observable_truth/forbidden_misreadings:4` |
| resourceIntent:1 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:1`; `draft_technical_closure/conditional_requirements:2` |
| resourceIntent:2 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:2`; `draft_technical_closure/unknown_inputs:1` |
| notRequiredAsNewResources:1 | `image_observable_truth/actors_logical_external:1` |
| notRequiredAsNewResources:2 | `image_observable_truth/actors_logical_external:1`; `image_observable_truth/forbidden_misreadings:3` |
| unknownInputs:1 | `draft_technical_closure/unknown_inputs:1` |

## Case E — Containerized scalable web application

[Official page/context](https://docs.aws.amazon.com/solutions/building-a-containerized-and-scalable-web-application-on-aws/) · [Exact input image](https://docs.aws.amazon.com/images/solutions/building-a-containerized-and-scalable-web-application-on-aws/images/building-a-containerized-and-scalable-web-application-on-aws-1.png)

![Case E official input](https://docs.aws.amazon.com/images/solutions/building-a-containerized-and-scalable-web-application-on-aws/images/building-a-containerized-and-scalable-web-application-on-aws-1.png)

Raw SHA-256 `2ba2f1d238026058bde92c21f88ba9263dcd9e5341606ad80155239564c4ccaa`; 2968 × 1600; 177632 bytes; `image/png`.
Page accessed `2026-10-07T14:59:29.992758+09:00`; image accessed `2026-10-07T14:59:55.421036+09:00`. Page snapshot SHA-256 `47e518ed1982b805ac074bb4b65cf04d440d63ae42cc2e42b9549d1c46c80d44`.

High service diversity, two AZs, static/dynamic split, dense/crossing arrows and operational side paths stress architecture fidelity.

### image_observable_truth

**Visible components/service families**

- Route 53, labeled Amazon Route 53.
- Cognito, labeled Amazon Cognito.
- CloudFront, labeled Amazon CloudFront.
- S3, labeled Amazon S3 Static storage.
- API Gateway, labeled Amazon API Gateway.
- One Application Load Balancer symbol/label.
- ECS and Fargate symbols/labels in private app tiers in both AZs.
- DynamoDB symbols/labels in each depicted private database tier.
- ECR, labeled Amazon ECR.
- CloudWatch, labeled Amazon CloudWatch.
- One VPC with AZ 1/AZ 2 boundaries, each enclosing private app and private database subnet boxes.

**Visible/necessarily encoded directed relationships**

- The client -> Route 53 -> Cognito -> CloudFront path is depicted as logical access/DNS/authentication sequencing, not literal DNS compute proxying.
- CloudFront branches to S3 Static storage and to API Gateway -> ALB -> ECS/Fargate capacity in both AZs.
- Application capacity has bidirectional data-tier edges to DynamoDB in each AZ grouping.
- ECR has an image-supply edge to the upper ECS application tier; it is a side supply path, not serial user-request processing.
- Application, ALB and DynamoDB paths lead to CloudWatch monitoring; they are side telemetry paths, not the application data/request chain.

**Visible topology associations**

- Private app/data-tier groupings are repeated across two AZs around one shared ALB; the image contains no internet-facing/public ALB label.

**Meaningful containment/boundaries**

- One VPC encloses AZ 1 and AZ 2; each AZ has a labeled Private app subnet and Private database subnet box.
- ECS/Fargate capacity is shown inside the private app boxes; DynamoDB symbols are shown in the data-tier boxes. Record this visible logical grouping, not a demand for impossible provider subnet placement.
- Route 53/Cognito/CloudFront/S3/API/ECR/CloudWatch are outside the VPC app/data boxes; ALB connects both application tiers.

**Material cardinality**

- Two AZ application/data-tier groupings and one logical ALB.
- Repeated ECS/DynamoDB symbols preserve two-AZ application/data intent but do not establish exact cluster/service/table provisioning counts.
- Both static and dynamic branches plus the ECR supply and CloudWatch monitoring side paths must remain distinguishable.

**Forbidden depicted-architecture misreadings**

- Replacing the visible ECS/Fargate compute with EKS, EC2 or Lambda, or DynamoDB with RDS.
- Omitting the static S3/CloudFront branch or the dynamic API Gateway/ALB/Fargate chain.
- Treating ECR supply or CloudWatch telemetry as serial user-request processing.
- Collapsing the two-AZ architecture or inferring mandatory duplicate clusters/tables solely from repeated symbols.
- Adding an unrelated second architecture to fill unspecified account/network values.

**Visible service/resource intent**

- Preserve all visible service capabilities, static/dynamic branches, two-AZ private app/data-tier intent, ECR supply and CloudWatch monitoring. No internet-facing ALB property or newly created resource per repeated icon is an image requirement.

**Actors/logical/external concepts**

- Client, numbered steps, logical DNS/auth flow and tier containers are context/control grouping; existing networks, images, identity and other service references may be declared inputs.

### documentation_context

Context/disambiguation only; **not additional mandatory semantic truth**.

**Disambiguation**

- The numbered authored explanation names service roles and clarifies supply/monitoring side paths and conceptual tier boxes.
- DynamoDB is a regional managed service; its drawn data-tier grouping is conceptual, not an actual deployable subnet attachment.

**Page-only detail**

- The page calls the ALB internet-facing. The exact image has no such label or public-subnet boundary, so absence of this property is not an image semantic failure.
- Public ALB subnets/routes, exact Cognito/identity flow, callback/certificate/domain settings, API integration details, IAM policies, listener/health settings and operational monitoring implementation are not specified by the image.

Authored context sections: How it works, Steps 1 through 10.

### draft_technical_closure

Only relationships/properties chosen by the generated draft; page-only internet-facing/public network properties do not become obligatory.

- If the draft realizes CloudFront static S3 access, supply coherent origin/access authorization or explicit valid external capability; accept equivalent valid mechanisms.
- If it realizes API Gateway -> ALB -> Fargate, its chosen API/backend/listener/target/task networking must be coherent, with legitimate editable inputs.
- Only if the generated draft chooses an internet-facing ALB must its selected public subnet/routing capability be coherent. Do not require that choice just because the page describes it.
- Chosen ECS/ECR image supply, DynamoDB access and CloudWatch integrations need coherent valid references/permissions or supplied boundaries; exact policy/telemetry settings are not image truth.
- Never implement a nonexistent DynamoDB subnet argument or require one table/cluster per AZ icon. Preserve logical depicted grouping while satisfying the provider schema.

Unknown/personalized inputs:

- Domains/zone/certificate identifiers, IdP/callback/client settings, account/region/role/secrets, container images, ports/health settings, network CIDRs/IDs and table key details may remain variables/coherent external references/editable inputs.

Common real CLI/provider validity and REVIEWABLE_IAC_DRAFT rules above apply.

### Remaining limitations

- The official drawing uses conceptual DynamoDB/data-subnet placement; no physically impossible provider placement is demanded.
- Page-only internet-facing detail is context. The image-only case is not a fully specified network/auth deployment contract.
- This dense service graph is still one bounded official sample, not universal generalization.

### Audit of every prior requirement

Source IDs identify exact fields/items in the [superseded manifest](../../evaluation/terraformers-aws-official-v1/superseded/candidate-01-manifest.json). Mixed assertions are split; image-only items remain mandatory. Targets identify current authority/field/item, not production code.

| Prior source item | Current authority destinations |
| --- | --- |
| coreComponents:1 | `image_observable_truth/components:1` |
| coreComponents:2 | `image_observable_truth/components:2` |
| coreComponents:3 | `image_observable_truth/components:3` |
| coreComponents:4 | `image_observable_truth/components:4` |
| coreComponents:5 | `image_observable_truth/components:5` |
| coreComponents:6 | `image_observable_truth/components:6` |
| coreComponents:7 | `image_observable_truth/components:7` |
| coreComponents:8 | `image_observable_truth/components:8` |
| coreComponents:9 | `image_observable_truth/components:9` |
| coreComponents:10 | `image_observable_truth/components:10` |
| coreComponents:11 | `image_observable_truth/components:11`; `image_observable_truth/containment:1` |
| directedRelationships:1 | `image_observable_truth/directed_relationships:1` |
| directedRelationships:2 | `image_observable_truth/directed_relationships:2`; `draft_technical_closure/conditional_requirements:1`; `draft_technical_closure/conditional_requirements:2` |
| directedRelationships:3 | `image_observable_truth/directed_relationships:3`; `draft_technical_closure/conditional_requirements:4` |
| directedRelationships:4 | `image_observable_truth/directed_relationships:4`; `draft_technical_closure/conditional_requirements:4` |
| directedRelationships:5 | `image_observable_truth/directed_relationships:5`; `draft_technical_closure/conditional_requirements:4` |
| directedRelationships:6 | `draft_technical_closure/conditional_requirements:1`; `draft_technical_closure/conditional_requirements:2`; `draft_technical_closure/conditional_requirements:4` |
| containment:1 | `image_observable_truth/containment:1` |
| containment:2 | `image_observable_truth/containment:2` |
| containment:3 | `image_observable_truth/containment:2`; `documentation_context/icon_or_logical_disambiguation:2`; `draft_technical_closure/conditional_requirements:5` |
| containment:4 | `documentation_context/page_only_details:1`; `draft_technical_closure/conditional_requirements:3` |
| materialCardinality:1 | `image_observable_truth/material_cardinality:1`; `image_observable_truth/material_cardinality:2` |
| materialCardinality:2 | `image_observable_truth/material_cardinality:2` |
| materialCardinality:3 | `image_observable_truth/material_cardinality:3` |
| forbiddenInterpretations:1 | `image_observable_truth/forbidden_misreadings:1` |
| forbiddenInterpretations:2 | `image_observable_truth/forbidden_misreadings:2` |
| forbiddenInterpretations:3 | `image_observable_truth/forbidden_misreadings:3` |
| forbiddenInterpretations:4 | `image_observable_truth/material_cardinality:2`; `image_observable_truth/forbidden_misreadings:4`; `draft_technical_closure/conditional_requirements:5` |
| forbiddenInterpretations:5 | `image_observable_truth/forbidden_misreadings:5` |
| resourceIntent:1 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:1`; `draft_technical_closure/conditional_requirements:2` |
| resourceIntent:2 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:4` |
| resourceIntent:3 | `image_observable_truth/resource_intent:1`; `draft_technical_closure/conditional_requirements:4`; `draft_technical_closure/conditional_requirements:5` |
| resourceIntent:4 | `documentation_context/page_only_details:1`; `draft_technical_closure/conditional_requirements:3` |
| notRequiredAsNewResources:1 | `image_observable_truth/actors_logical_external:1` |
| notRequiredAsNewResources:2 | `image_observable_truth/actors_logical_external:1`; `draft_technical_closure/unknown_inputs:1` |
| notRequiredAsNewResources:3 | `image_observable_truth/material_cardinality:2`; `documentation_context/icon_or_logical_disambiguation:2`; `draft_technical_closure/conditional_requirements:5` |
| unknownInputs:1 | `draft_technical_closure/unknown_inputs:1` |
