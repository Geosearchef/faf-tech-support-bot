package de.geosearchef;

import lombok.Data;

@Data
class Config {
	private final int commandCooldown;
	private final Command[] commands;
}
