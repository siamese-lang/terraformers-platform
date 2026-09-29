package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.EvaluationTrace.ReferenceHit;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import java.util.List;

/** Evaluation-only seam for A3 comparisons that holds extracted facts constant. */
public final class FixedFactsRetrievalProbe {
    private final RetrievalQueryTextBuilder queryTextBuilder;
    private final ReferenceRetriever retriever;

    public FixedFactsRetrievalProbe(RetrievalQueryTextBuilder queryTextBuilder, ReferenceRetriever retriever) {
        this.queryTextBuilder = queryTextBuilder;
        this.retriever = retriever;
    }

    public EvaluationTrace.RetrievalEvidence execute(ArchitectureRetrievalFacts facts, int topK) {
        ReferenceQuery query = new ReferenceQuery(queryTextBuilder.build(facts), topK);
        List<ReferenceDocument> documents = retriever.retrieve(query);
        List<ReferenceHit> hits = java.util.stream.IntStream.range(0, documents.size()).mapToObj(index -> {
            ReferenceDocument d = documents.get(index);
            return new ReferenceHit(index + 1, d.id(), d.score(), d.title(), d.authority(), d.documentType(),
                    d.sourcePath(), d.resourceTypes(), d.providerVersion(), d.corpusVersion(), d.priority(), d.riskTags());
        }).toList();
        return new EvaluationTrace.RetrievalEvidence(query.text(), query.resourceTypes(), query.limit(), hits);
    }
}
