package de.geosearchef;

import lombok.Data;

@Data
class Command {
	private final String type;
	private final String[] cmd;
	private final String response;
	private final Predicate predicate;
}
