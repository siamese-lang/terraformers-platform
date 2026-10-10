package com.terraformers.modernization.analysis;

import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GeneratedTerraformContractInspector {

    private static final Pattern DEPLOYABLE = Pattern.compile(
            "(?m)^\\s*(resource|module)\\s+\"([^\"]+)\"(?:\\s+\"[^\"]+\")?\\s*\\{");
    private final AwsProviderSchemaCatalog catalog;

    public GeneratedTerraformContractInspector(@Lazy AwsProviderSchemaCatalog catalog) {
        this.catalog = catalog;
    }

    public void inspect(String terraform) {
        for (String type : resourceTypes(terraform)) {
            if (!type.startsWith("aws_") || !catalog.contains(type)) {
                throw new GeneratedTerraformContractViolation(
                        GeneratedTerraformContractViolation.Reason.RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT);
            }
        }
    }

    /**
     * Extracts deployable resource types through the same parser used by the production contract
     * inspector. The method intentionally does not validate provider membership so evidence-quality
     * assessment can distinguish an unknown generated resource from a resource that lacks selected
     * evidence.
     */
    public List<String> resourceTypes(String terraform) {
        String source = terraform == null ? "" : terraform;
        Matcher matcher = DEPLOYABLE.matcher(source);
        Set<String> resourceTypes = new LinkedHashSet<>();
        while (matcher.find()) {
            if (matcher.group(1).equals("module")) {
                throw new GeneratedTerraformContractViolation(
                        GeneratedTerraformContractViolation.Reason.MODULE_BLOCK);
            }
            resourceTypes.add(matcher.group(2));
        }
        return List.copyOf(resourceTypes);
    }

    /**
     * Detects absent authorization wiring, independently of provider/CLI validity. This is an
     * omission check for directly identifiable new S3 origins, not an IAM policy evaluator:
     * supplied policy expressions may remain editable references or deployment-specific inputs.
     */
    public boolean missingCloudFrontS3Authorization(String terraform) {
        List<Token> source = tokens(terraform == null ? "" : terraform);
        List<Block> resources = blocks(source, "resource");
        List<Block> locals = blocks(source, "locals");
        for (Block distribution : resources) {
            if (!distribution.type().equals("aws_cloudfront_distribution")) continue;
            for (Block origin : blocks(distribution.body(), "origin")) {
                if (emptyExpression(attribute(origin.body(), "origin_access_control_id"))) continue;
                String domain = expression(resolveAliases(attribute(origin.body(), "domain_name"), locals, 0));
                for (Block bucket : resources) {
                    if (!bucket.type().equals("aws_s3_bucket")) continue;
                    if (!referencesBucket(domain, bucket.name(), "bucket_regional_domain_name|bucket_domain_name")) continue;
                    if (!hasSuppliedAuthorization(resources, bucket, locals)) return true;
                }
            }
        }
        return false;
    }

    /**
     * An Amazon-issued certificate request does not wait for issuance. Detect a CloudFront
     * consumer of that request ARN without a matching validation dependency. External/data
     * certificates and explicit imports remain editable boundaries, not new pending requests.
     * This checks declared ordering only, not DNS ownership, certificate status or region.
     */
    public boolean missingCloudFrontCertificateValidation(String terraform) {
        List<Token> source = tokens(terraform == null ? "" : terraform);
        List<Block> resources = blocks(source, "resource");
        List<Block> locals = blocks(source, "locals");
        for (Block distribution : resources) {
            if (!distribution.type().equals("aws_cloudfront_distribution")) continue;
            for (Block viewer : blocks(distribution.body(), "viewer_certificate")) {
                List<Token> arn = resolveAliases(attribute(viewer.body(), "acm_certificate_arn"), locals, 0);
                for (Block certificate : resources) {
                    if (!certificate.type().equals("aws_acm_certificate")) continue;
                    List<Token> method = resolveAliases(attribute(certificate.body(), "validation_method"), locals, 0);
                    if (method.size() != 1 || !method.get(0).quoted()
                            || !Set.of("DNS", "EMAIL").contains(method.get(0).value())) continue;
                    List<List<Token>> addresses = certificateArnAddresses(arn, certificate);
                    if (addresses.isEmpty()) continue;
                    // An import declares only its exact instance; another index is not an external boundary.
                    if (addresses.stream().allMatch(address -> blocks(source, "import").stream().anyMatch(block ->
                            attribute(block.body(), "to").equals(address)
                            && !emptyExpression(attribute(block.body(), "id"))))) continue;
                    boolean ordered = resources.stream()
                            .filter(block -> block.type().equals("aws_acm_certificate_validation"))
                            .filter(block -> !certificateArnAddresses(resolveAliases(
                                    attribute(block.body(), "certificate_arn"), locals, 0), certificate).isEmpty())
                            .anyMatch(block -> dependsOn(distribution, block, resources, locals, new LinkedHashSet<>()));
                    if (!ordered) return true;
                }
            }
        }
        return false;
    }

    private List<List<Token>> certificateArnAddresses(List<Token> value, Block certificate) {
        List<Token> references = new ArrayList<>();
        for (Token token : value) {
            // Keep quoted instance keys; is() below excludes literal strings as traversal starts.
            references.add(token);
            if (token.quoted()) {
                Matcher interpolation = Pattern.compile("(?<!\\$)\\$\\{([^}]+)}").matcher(token.value());
                while (interpolation.find()) references.addAll(tokens(interpolation.group(1)));
            }
        }
        String address = certificate.type() + "." + certificate.name();
        List<List<Token>> result = new ArrayList<>();
        for (int i = 0; i < references.size(); i++) {
            if (references.get(i).is(address + ".arn")) result.add(List.of(new Token(address, false)));
            if (!references.get(i).is(address) || i + 1 >= references.size()
                    || !references.get(i + 1).is("[")) continue;
            int end = i + 2;
            int depth = 1;
            while (end < references.size() && depth > 0) {
                if (references.get(end).is("[")) depth++;
                if (references.get(end).is("]")) depth--;
                end++;
            }
            if (depth == 0 && end < references.size() && references.get(end).is(".arn")) {
                result.add(List.copyOf(references.subList(i, end)));
            }
        }
        return result;
    }

    private boolean dependsOn(Block consumer, Block validation, List<Block> resources,
            List<Block> locals, Set<String> visited) {
        String identity = consumer.type() + "." + consumer.name();
        if (!visited.add(identity)) return false;
        if (identity.equals(validation.type() + "." + validation.name())) return true;
        // Terraform uses both explicit depends_on and expression references as ordering edges.
        String dependencies = expression(resolveAliases(consumer.body(), locals, 0));
        for (Block resource : resources) {
            if (referencesResource(dependencies, resource.type(), resource.name(), null)
                    && dependsOn(resource, validation, resources, locals, visited)) return true;
        }
        return false;
    }

    private boolean referencesResource(String expression, String type, String name, String attribute) {
        return Pattern.compile("(?<![\\w.])" + Pattern.quote(type) + "\\." + Pattern.quote(name)
                + "(?:\\[[^\\]]+\\])?" + (attribute == null ? "" : "\\." + Pattern.quote(attribute))
                + (attribute == null ? "(?![\\w-])" : "(?![\\w.-])")).matcher(expression).find();
    }

    private boolean hasSuppliedAuthorization(List<Block> resources, Block bucket, List<Block> locals) {
        // Legacy inline policy / public ACL and separate bucket policies / public ACLs are
        // alternative declarations. No particular policy template or resource name is required.
        if (!emptyExpression(attribute(bucket.body(), "policy")) || publicReadAcl(bucket.body())) return true;
        for (Block resource : resources) {
            if (!Set.of("aws_s3_bucket_policy", "aws_s3_bucket_acl").contains(resource.type())) continue;
            List<Token> target = resolveAliases(attribute(resource.body(), "bucket"), locals, 0);
            boolean sameBucket = referencesBucket(expression(target), bucket.name(), "id|bucket")
                    || (!target.isEmpty() && target.equals(resolveAliases(attribute(bucket.body(), "bucket"), locals, 0)));
            if (!sameBucket) continue;
            if (resource.type().equals("aws_s3_bucket_policy")
                    && !emptyExpression(attribute(resource.body(), "policy"))) return true;
            if (resource.type().equals("aws_s3_bucket_acl") && publicReadAcl(resource.body())) return true;
        }
        return false;
    }

    private boolean publicReadAcl(List<Token> body) {
        List<Token> acl = attribute(body, "acl");
        if (acl.size() == 1 && acl.get(0).quoted()
                && Set.of("public-read", "public-read-write").contains(acl.get(0).value())) return true;
        for (Block policy : blocks(body, "access_control_policy")) {
            for (Block grant : blocks(policy.body(), "grant")) {
                List<Token> permission = attribute(grant.body(), "permission");
                if (permission.size() != 1 || !permission.get(0).quoted()
                        || !Set.of("READ", "FULL_CONTROL").contains(permission.get(0).value())) continue;
                for (Block grantee : blocks(grant.body(), "grantee")) {
                    List<Token> uri = attribute(grantee.body(), "uri");
                    if (uri.size() == 1 && uri.get(0).quoted()
                            && uri.get(0).value().equals("http://acs.amazonaws.com/groups/global/AllUsers")) return true;
                }
            }
        }
        return false;
    }

    private List<Token> resolveAliases(List<Token> value, List<Block> locals, int depth) {
        if (depth >= 8) return value; // Cyclic/complex expressions are not evaluated here.
        List<Token> result = new ArrayList<>();
        for (Token token : value) {
            String reference = token.quoted() && token.value().matches("\\$\\{local\\.[\\w-]+}")
                    ? token.value().substring(2, token.value().length() - 1) : token.value();
            if ((!token.quoted() || !reference.equals(token.value())) && reference.matches("local\\.[\\w-]+")) {
                List<Token> replacement = locals.stream().map(block -> attribute(block.body(), reference.substring(6)))
                        .filter(tokens -> !tokens.isEmpty()).findFirst().orElse(List.of());
                if (!replacement.isEmpty()) { result.addAll(resolveAliases(replacement, locals, depth + 1)); continue; }
            }
            result.add(token);
        }
        return List.copyOf(result);
    }

    private boolean referencesBucket(String expression, String name, String attributes) {
        return Pattern.compile("(?<![\\w.])aws_s3_bucket\\." + Pattern.quote(name)
                + "(?:\\[[^\\]]+\\])?\\.(?:" + attributes + ")(?![\\w])").matcher(expression).find();
    }

    private boolean emptyExpression(List<Token> value) {
        return value.isEmpty() || (value.size() == 1
                && (value.get(0).value().isBlank() || (!value.get(0).quoted() && value.get(0).value().equals("null"))));
    }

    private String expression(List<Token> value) {
        StringBuilder result = new StringBuilder();
        for (Token token : value) {
            if (!token.quoted()) result.append(token.value());
            else {
                // Literal examples are not references; only real string interpolations count.
                Matcher interpolation = Pattern.compile("(?<!\\$)\\$\\{([^}]+)}").matcher(token.value());
                while (interpolation.find()) result.append(interpolation.group(1)).append(' ');
            }
        }
        return result.toString();
    }

    private List<Token> attribute(List<Token> body, String name) {
        int depth = 0;
        for (int i = 0; i + 1 < body.size(); i++) {
            Token token = body.get(i);
            if (depth == 0 && token.is(name) && body.get(i + 1).is("=")) {
                int end = i + 2;
                int valueDepth = 0;
                while (end < body.size()) {
                    Token next = body.get(end);
                    if (valueDepth == 0 && (next.is("\n")
                            || (end + 1 < body.size() && body.get(end + 1).is("=")))) break;
                    valueDepth += nesting(next);
                    end++;
                }
                return List.copyOf(body.subList(i + 2, end));
            }
            depth += nesting(token);
        }
        return List.of();
    }

    private int nesting(Token token) {
        if (token.quoted()) return 0;
        return switch (token.value()) {
            case "{", "[", "(" -> 1;
            case "}", "]", ")" -> -1;
            default -> 0;
        };
    }

    private List<Block> blocks(List<Token> tokens, String keyword) {
        List<Block> result = new ArrayList<>();
        int depth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (depth == 0 && token.is(keyword)) {
                int open = i + 1;
                List<String> labels = new ArrayList<>();
                while (open < tokens.size() && tokens.get(open).quoted()) labels.add(tokens.get(open++).value());
                if (open < tokens.size() && tokens.get(open).is("{")) {
                    int end = open + 1;
                    int braces = 1;
                    while (end < tokens.size() && braces > 0) {
                        if (tokens.get(end).is("{")) braces++;
                        if (tokens.get(end).is("}")) braces--;
                        end++;
                    }
                    if (braces == 0) {
                        result.add(new Block(labels.size() > 0 ? labels.get(0) : "",
                                labels.size() > 1 ? labels.get(1) : "", List.copyOf(tokens.subList(open + 1, end - 1))));
                        i = end - 1;
                        continue;
                    }
                }
            }
            depth += nesting(token);
        }
        return result;
    }

    /** Small lexical block reader: strings, heredocs and comments never become declarations. */
    private List<Token> tokens(String source) {
        List<Token> result = new ArrayList<>();
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (c == '\n') { result.add(new Token("\n", false)); i++; }
            else if (Character.isWhitespace(c)) i++;
            else if (c == '#' || source.startsWith("//", i)) {
                while (i < source.length() && source.charAt(i) != '\n') i++;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 2;
            } else if (c == '"') {
                StringBuilder value = new StringBuilder();
                i++;
                while (i < source.length()) {
                    char next = source.charAt(i++);
                    if (next == '"') break;
                    if (next == '\\' && i < source.length()) value.append(source.charAt(i++));
                    else value.append(next);
                }
                result.add(new Token(value.toString(), true));
            } else if (source.startsWith("<<", i)) {
                int headerEnd = source.indexOf('\n', i);
                if (headerEnd < 0) break;
                String delimiter = source.substring(i + 2, headerEnd).replaceFirst("^-", "").strip();
                int end = headerEnd + 1;
                while (end < source.length()) {
                    int lineEnd = source.indexOf('\n', end);
                    if (lineEnd < 0) lineEnd = source.length();
                    if (source.substring(end, lineEnd).strip().equals(delimiter)) break;
                    end = lineEnd < source.length() ? lineEnd + 1 : lineEnd;
                }
                result.add(new Token(source.substring(headerEnd + 1, end), true));
                i = end;
                while (i < source.length() && source.charAt(i) != '\n') i++;
            } else if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                int start = i++;
                while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i))
                        || "_.-".indexOf(source.charAt(i)) >= 0)) i++;
                result.add(new Token(source.substring(start, i), false));
            } else result.add(new Token(String.valueOf(source.charAt(i++)), false));
        }
        return result;
    }

    private record Token(String value, boolean quoted) {
        boolean is(String literal) { return !quoted && value.equals(literal); }
    }

    private record Block(String type, String name, List<Token> body) {}
}
