/*
 * Copyright 2022-2026 the original author or authors.
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
import static tech.units.indriya.unit.Units.LITRE;
import static tech.units.indriya.unit.Units.METRE;
import static tech.units.indriya.unit.Units.MINUTE;
import static tech.units.indriya.unit.Units.SECOND;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Speed;

import tech.units.indriya.unit.ProductUnit;

/**
 * A bunch of additional sporty units.
 *
 * @author Michael J. Simons
 * @since 1.0.0
 */
final class AdditionalUnits {

	/**
	 * VO2 Max type.
	 */
	interface VO2Max extends Quantity<VO2Max> {
	}

	/**
	 * Pace type.
	 */
	interface Pace extends Quantity<Pace> {
	}

	/**
	 * Garmin uses cm/ms in their files.
	 */
	static final Unit<Speed> CENTIMETRE_PER_SECOND = new ProductUnit<>(CENTI(METRE).divide(MILLI(SECOND)));

	/**
	 * Usable to represent inverted {@link Speed} in a proper unit (min/km).
	 */
	static final Unit<Pace> MINUTES_PER_KM = new ProductUnit<>(MINUTE.divide(KILO(METRE)));

	/**
	 * Standard definition of <a href="https://en.wikipedia.org/wiki/VO2_max">VO2 Max</a>.
	 */
	static final Unit<VO2Max> VO2_MAX = new ProductUnit<>(MILLI(LITRE).divide(MINUTE.multiply(tech.units.indriya.unit.Units.KILOGRAM)));

	private AdditionalUnits() {
	}
}
