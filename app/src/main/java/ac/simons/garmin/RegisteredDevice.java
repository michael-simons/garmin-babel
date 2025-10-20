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

/**
 * Representation of a device registered with Garmin. Garmin refers to the {@link #deviceId()}
 * as <a href="https://support.garmin.com/en-US/?faq=VwxMAbrHM2796caHMwdt58">unitId</a>, too,
 * but I want to have it consistent here.
 *
 * @author Michael J. Simons
 * @param deviceId     The units global unique id
 * @param productName  The actual product name
 * @param serialNumber The individual serial number
 * @param partNumber   The SKU at Garmin
 * @since 1.0.0
 */
record RegisteredDevice(Long deviceId, String productName, String serialNumber, String partNumber) {
}
