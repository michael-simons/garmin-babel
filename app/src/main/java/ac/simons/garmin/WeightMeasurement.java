/*
 * Copyright 2022-2025 the original author or authors.
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

import java.time.Instant;

import javax.measure.Quantity;
import javax.measure.quantity.Mass;

/**
 * Representation of a weight measurement on a given moment.
 *
 * @author Michael J. Simons
 * @param measuredOn The moment in time the measure was taken
 * @param value      The actual value
 * @since 1.0.0
 */
public record WeightMeasurement(Instant measuredOn, Quantity<Mass> value) {
}
