package com.traveler.useractivity.domain.process.dedup.model;

import com.traveler.useractivity.domain.rule.dedup.definition.DedupSpec;
import java.util.List;

public record ActiveDedupRule(Long dedupRuleId, String name, List<DedupSpec> specs) {}
