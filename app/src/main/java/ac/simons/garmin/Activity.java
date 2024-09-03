/*
 * Copyright 2022-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ac.simons.garmin;

import static javax.measure.MetricPrefix.CENTI;
import static tech.units.indriya.unit.Units.METRE;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;

import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

/**
 * Represents the content of an activity I am interested in. Most of the fields are optional, please check before using.
 *
 * @author Michael J. Simons
 * @param garminId        The id of this activity as used by Garmin
 * @param deviceId        The id of the {@link RegisteredDevice#deviceId() registered device}
 * @param name            Name given in Garmin connect
 * @param startedOn       Start date
 * @param activityType    Type of the activity (more detailed than the general {@link #sportType}.
 * @param sportType       A broader kind of sport
 * @param distance        Distance of this activity
 * @param elevationGain   The elevation gain
 * @param avgSpeed        Average speed
 * @param maxSpeed        Maximum speed
 * @param duration        Complete duration
 * @param elapsedDuration Elapsed duration (unsure how this is different from {@link #duration}).
 * @param movingDuration  The duration in movement
 * @param vO2Max          VO2Max if available
 * @param startLongitude  Longitude on which the activity startet
 * @param startLatitude   Latitude on which the activity startet
 * @param endLongitude    Longitude on which the activity ended
 * @param endLatitude     Latitude on which the activity ended
 * @param gear            Mutable list of gear.
 * @since 1.0.0
 */
record Activity(
	long garminId,
	Long deviceId,
	String name,
	Instant startedOn,
	String activityType,
	String sportType,
	ComparableQuantity<Length> distance,
	ComparableQuantity<Length> elevationGain,
	ComparableQuantity<Speed> avgSpeed,
	ComparableQuantity<Speed> maxSpeed,
	Duration duration,
	Duration elapsedDuration,
	Duration movingDuration,
	Quantity<AdditionalUnits.VO2Max> vO2Max,
	BigDecimal startLongitude,
	BigDecimal startLatitude,
	BigDecimal endLongitude,
	BigDecimal endLatitude,
	List<String> gear
) {

	static Activity of(Map<String, ?> source) {

		Instant startedOn;
		if (source.containsKey("beginTimestamp")) {
			startedOn = Instant.ofEpochMilli((Long) source.get("beginTimestamp"));
		} else {
			startedOn = ZonedDateTime.ofInstant(Instant.ofEpochMilli(((BigDecimal) source.get("startTimeGmt")).longValue()), ZoneId.of("GMT")).toInstant();
		}

		return new Activity(
			((Number) source.get("activityId")).longValue(),
			source.containsKey("deviceId") ? ((Number) source.get("deviceId")).longValue() : null,
			(String) source.get("name"),
			startedOn,
			(String) source.get("activityType"),
			(String) source.get("sportType"),
			source.containsKey("distance") ? Quantities.getQuantity((BigDecimal) source.get("distance"), CENTI(METRE)) : null,
			source.containsKey("elevationGain") ? Quantities.getQuantity((BigDecimal) source.get("elevationGain"), CENTI(METRE)) : null,
			source.containsKey("avgSpeed") ? Quantities.getQuantity((BigDecimal) source.get("avgSpeed"), AdditionalUnits.CENTIMETRE_PER_SECOND) : null,
			source.containsKey("maxSpeed") ? Quantities.getQuantity((BigDecimal) source.get("maxSpeed"), AdditionalUnits.CENTIMETRE_PER_SECOND) : null,
			source.containsKey("duration") ? duration((BigDecimal) source.get("duration")) : null,
			source.containsKey("elapsedDuration") ? duration((BigDecimal) source.get("elapsedDuration")) : null,
			source.containsKey("movingDuration") ? duration((BigDecimal) source.get("movingDuration")) : null,
			source.containsKey("vO2MaxValue") ? Quantities.getQuantity((BigDecimal) source.get("vO2MaxValue"), AdditionalUnits.VO2_MAX) : null,
			source.containsKey("startLongitude") ? (BigDecimal) source.get("startLongitude") : null,
			source.containsKey("startLatitude") ? (BigDecimal) source.get("startLatitude") : null,
			source.containsKey("endLongitude") ? (BigDecimal) source.get("endLongitude") : null,
			source.containsKey("endLatitude") ? (BigDecimal) source.get("endLatitude") : null,
			new ArrayList<>()
		);
	}

	private static Duration duration(BigDecimal value) {
		return Duration.ofMillis(value.longValue()).plusNanos(value.remainder(BigDecimal.ONE).movePointRight(6).longValue());
	}
}
