package com.traveler.useractivity.domain.process.dedup.model;

import com.traveler.useractivity.domain.rule.dedup.definition.DedupSpec;

public record DedupResult(ActiveDedupRule rule, DedupSpec matchedSpec) {}
