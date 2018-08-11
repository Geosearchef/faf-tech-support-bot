package de.geosearchef;

import lombok.Data;

import java.util.Arrays;

@Data
class Predicate {
	private final ConjunctiveClause[] clauses;

	boolean evaluate(String s) {
		return s!= null && Arrays.stream(clauses).anyMatch(clause -> Arrays.stream(clause.getLiterals()).allMatch(s::contains));
	}
}
