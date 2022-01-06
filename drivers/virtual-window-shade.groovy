/**
 *  Copyright 2018, 2019 SmartThings
 *
 *  Provides a simulated window shade.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Modified by Brian Ujvary
 *
 *  Change Log:
 *    11/09/2021 v1.0 - Simulate the open/close events of a Zigbee shade
 *
 */
import groovy.json.JsonOutput

metadata {
	definition (name: "Virtual Window Shade", namespace: "bujvary", author: "SmartThings") {
		capability "Actuator"
		capability "WindowShade"
		capability "SwitchLevel"

		// Commands to use in the simulator
		command "openPartially"
		command "closePartially"
		command "partiallyOpen"
		command "opening"
		command "closing"
		command "opened"
		command "closed"
		command "unknown"
	}

	preferences {
		section {
			input("actionDelay", "number",
				title: "Transition time",
				description: "<div><i>In seconds (default: 10 Seconds)</i></div>",
				range: "1..120", displayDuringSetup: false)
		}
		section {
			input("supportedCommands", "enum",
				title: "Supported Commands\n\nThis controls the value for supportedWindowShadeCommands.",
				description: "open, close, pause", defaultValue: "2", multiple: false,
				options: [
					"1": "open, close",
					"2": "open, close, pause",
					"3": "open",
					"4": "close",
					"5": "pause",
					"6": "open, pause",
					"7": "close, pause",
					"8": "<empty list>",
					// For testing OCF/mobile client bugs
					"9": "open, closed, pause",
					"10": "open, closed, close, pause"
				]
			)
		}
	}
}

private getSupportedCommandsMap() {
	[
		"1": ["open", "close"],
		"2": ["open", "close", "pause"],
		"3": ["open"],
		"4": ["close"],
		"5": ["pause"],
		"6": ["open", "pause"],
		"7": ["close", "pause"],
		"8": [],
		// For testing OCF/mobile client bugs
		"9": ["open", "closed", "pause"],
		"10": ["open", "closed", "close", "pause"]
	]
}

private getShadeActionDelay() {
	(settings.actionDelay != null) ? settings.actionDelay : 10
}

def installed() {
	log.debug "installed()"

	updated()
	opened()
}

def updated() {
	log.debug "updated()"

	def commands = (settings.supportedCommands != null) ? settings.supportedCommands : "2"

	sendEvent(name: "supportedWindowShadeCommands", value: JsonOutput.toJson(supportedCommandsMap[commands]))
}

def parse(String description) {
	log.debug "parse(): $description"
}

// Capability commands

// TODO: Implement a state machine to fine tune the behavior here.
// Right now, tapping "open" and then "pause" leads to "opening",
// "partially open", then "open" as the open() command completes.
// The `runIn()`s below should all call a marshaller to handle the
// movement to a new state. This will allow for shade level sim, too.

def open() {
	log.debug "open()"
    
    for (int i = 0; i < shadeActionDelay; i++) {
        if( i.toBigInteger().mod( 2 ) == 0 ) {
            opening()
        } else {
            partiallyOpen()
        }
        pauseExecution(1000)
    }
    
	opened()
	//runIn(shadeActionDelay, "opened")
}

def close() {
	log.debug "close()"
    
    for (int i = 0; i < shadeActionDelay; i++) {
        if( i.toBigInteger().mod( 2 ) == 0 ) {
            closing()
        } else {
            partiallyOpen()
        }
        pauseExecution(1000)
    }
    
    closed()
	//runIn(shadeActionDelay, "closed")
}

def pause() {
	log.debug "pause()"
	partiallyOpen()
}

def presetPosition() {
	log.debug "presetPosition()"
	if (device.currentValue("windowShade") == "open") {
		closePartially()
	} else if (device.currentValue("windowShade") == "closed") {
		openPartially()
	} else {
		partiallyOpen()
	}
}

// Custom test commands

def openPartially() {
	log.debug "openPartially()"
	opening()
	runIn(shadeActionDelay, "partiallyOpen")
}

def closePartially() {
	log.debug "closePartially()"
	closing()
	runIn(shadeActionDelay, "partiallyOpen")
}

def partiallyOpen() {
	log.debug "windowShade: partially open"
	sendEvent(name: "windowShade", value: "partially open", isStateChange: true)
}

def opening() {
	log.debug "windowShade: opening"
	sendEvent(name: "windowShade", value: "opening", isStateChange: true)
}

def closing() {
	log.debug "windowShade: closing"
	sendEvent(name: "windowShade", value: "closing", isStateChange: true)
}

def opened() {
	log.debug "windowShade: open"
	sendEvent(name: "windowShade", value: "open", isStateChange: true)
}

def closed() {
	log.debug "windowShade: closed"
	sendEvent(name: "windowShade", value: "closed", isStateChange: true)
}

def unknown() {
	// TODO: Add some "fuzzing" logic so that this gets hit every now and then?
	log.debug "windowShade: unknown"
	sendEvent(name: "windowShade", value: "unknown", isStateChange: true)
}
