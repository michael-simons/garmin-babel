/*
 * Copyright 2022 the original author or authors.
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
import static javax.measure.MetricPrefix.KILO;
import static javax.measure.MetricPrefix.MILLI;
import static tech.units.indriya.unit.Units.KILOGRAM;
import static tech.units.indriya.unit.Units.LITRE;
import static tech.units.indriya.unit.Units.METRE;
import static tech.units.indriya.unit.Units.MINUTE;
import static tech.units.indriya.unit.Units.SECOND;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.measure.quantity.Speed;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.ProductUnit;

/**
 * Program for massaging Garmin GDPR archive into something usable.
 *
 * @author Michael J. Simons
 */
public final class GarminBabel {

	private static final Unit<Pace> MINUTES_PER_KM = new ProductUnit<>(MINUTE.divide(KILO(METRE)));
	private static final Unit<Speed> CENTIMETRE_PER_SECOND = new ProductUnit<>(CENTI(METRE).divide(MILLI(SECOND)));
	private static final Unit<VO2Max> VO2_MAX = new ProductUnit<>(MILLI(LITRE).divide(MINUTE.multiply(KILOGRAM)));
	private static final TypeReference<Map<String, Object>> MAP_OF_OBJECTS = new TypeReference<>() {
	};

	record Activity(
		long garminId,
		String name,
		Instant startedOn,
		String activityType,
		String sportType,
		Quantity<Length> distance,
		Quantity<Length> elevationGain,
		Quantity<Speed> avgSpeed,
		Quantity<Speed> maxSpeed,
		Duration duration,
		Duration elapsedDuration,
		Duration movingDuration,
		Quantity<VO2Max> vO2Max
	) {
	}

	static Duration duration(BigDecimal value) {
		return Duration.ofMillis(value.longValue()).plusNanos(value.remainder(BigDecimal.ONE).movePointRight(6).longValue());
	}

	static Activity activity(Map<String, ?> source) {

		Instant startedOn;
		if (source.containsKey("beginTimestamp")) {
			startedOn = Instant.ofEpochMilli((Long) source.get("beginTimestamp"));
		} else {
			startedOn = ZonedDateTime.ofInstant(Instant.ofEpochMilli(((BigDecimal) source.get("startTimeGmt")).longValue()), ZoneId.of("GMT")).toInstant();
		}

		return new Activity(
			((Number) source.get("activityId")).longValue(),
			(String) source.get("name"),
			startedOn,
			(String) source.get("activityType"),
			(String) source.get("sportType"),
			source.containsKey("distance") ? Quantities.getQuantity((BigDecimal) source.get("distance"), CENTI(METRE)) : null,
			source.containsKey("elevationGain") ? Quantities.getQuantity((BigDecimal) source.get("elevationGain"), CENTI(METRE)) : null,
			source.containsKey("avgSpeed") ? Quantities.getQuantity((BigDecimal) source.get("avgSpeed"), CENTIMETRE_PER_SECOND) : null,
			source.containsKey("maxSpeed") ? Quantities.getQuantity((BigDecimal) source.get("maxSpeed"), CENTIMETRE_PER_SECOND) : null,
			source.containsKey("duration") ? duration((BigDecimal) source.get("duration")) : null,
			source.containsKey("elapsedDuration") ? duration((BigDecimal) source.get("elapsedDuration")) : null,
			source.containsKey("movingDuration") ? duration((BigDecimal) source.get("movingDuration")) : null,
			source.containsKey("vO2MaxValue") ? Quantities.getQuantity((BigDecimal) source.get("vO2MaxValue"), VO2_MAX) : null
		);
	}

	interface VO2Max extends Quantity<VO2Max> {
	}

	interface Pace extends Quantity<Pace> {
	}

	public static void main(String... a) throws IOException {

		var mapper = new ObjectMapper();
		mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
		var jsonFactory = mapper.getFactory();

		try (
			var in = Files.newInputStream(Path.of(a[0]));
			var parser = jsonFactory.createParser(in)
		) {
			if (parser.nextToken() != JsonToken.START_ARRAY) {
				throw new IllegalStateException("Expected content to be an array");
			}
			if (parser.nextToken() != JsonToken.START_OBJECT || !"summarizedActivitiesExport".equals(parser.nextFieldName()) || parser.nextToken() != JsonToken.START_ARRAY) {
				throw new IllegalStateException("Expected content to be the start of `summarizedActivitiesExport`");
			}

			List<Activity> activities = new ArrayList<>();
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				var source = mapper.readValue(parser, MAP_OF_OBJECTS);
				var activity = activity(source);
				activities.add(activity);
			}

			// Fasted run
			Optional<ComparableQuantity<Pace>> fastestRun = activities.stream()
				.filter(activity -> "running".equals(activity.activityType) && activity.avgSpeed != null)
				.map(activity -> (ComparableQuantity<Pace>) activity.avgSpeed.inverse().asType(Pace.class).to(MINUTES_PER_KM))
				.min(Comparable::compareTo);

			// Total distance
			var totalDistance = activities.stream()
				.filter(ac -> ac.distance != null)
				.map(Activity::distance)
				.reduce(Quantities.getQuantity(0, CENTI(METRE)), Quantity::add).to(KILO(METRE));
		}
	}
}
