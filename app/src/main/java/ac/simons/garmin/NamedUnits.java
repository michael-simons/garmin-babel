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

import static javax.measure.MetricPrefix.KILO;

import javax.measure.Quantity;
import javax.measure.Unit;

import tech.units.indriya.unit.Units;

/**
 * Named units for use in the CLI.
 *
 * @author Michael J. SImons
 * @since 1.0.0
 */
sealed interface NamedUnits permits NamedUnits.Distance, NamedUnits.Weight, NamedUnits.Speed {

	<T extends Quantity<T>> Unit<T> getUnit(Class<T> type);

	enum Distance implements NamedUnits {
		KILOMETRE(KILO(Units.METRE)),
		METRE(Units.METRE);

		final Unit<?> unit;

		Distance(Unit<?> unit) {
			this.unit = unit;
		}

		public <T extends Quantity<T>> Unit<T> getUnit(Class<T> type) {
			return unit.asType(type);
		}
	}

	enum Speed implements NamedUnits {
		KPH(Units.KILOMETRE_PER_HOUR),
		MPS(Units.METRE_PER_SECOND);

		final Unit<?> unit;

		Speed(Unit<?> unit) {
			this.unit = unit;
		}

		public <T extends Quantity<T>> Unit<T> getUnit(Class<T> type) {
			return unit.asType(type);
		}
	}

	enum Weight implements NamedUnits {
		GRAM(Units.GRAM),
		KILOGRAM(Units.KILOGRAM);

		final Unit<?> unit;

		Weight(Unit<?> unit) {
			this.unit = unit;
		}

		public <T extends Quantity<T>> Unit<T> getUnit(Class<T> type) {
			return unit.asType(type);
		}
	}
}
