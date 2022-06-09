/*
 *  Away Lighting v1.0
 *
 *    1.0 (06/09/2022)
 *      - Initial Release
 *
 *
 *  Copyright 2022 Brian Ujvary
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

definition(
	name: "Away Lighting",
    namespace: "bujvary",
    author: "Brian Ujvary",
    description: "Control lights when a virutal Away switch is turned on/off",
    singleInstance: true,
	category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/apps/away-lighting.groovy"
)

preferences {
	page(name:"pageMain")
}


def pageMain() {
	dynamicPage(name: "pageMain", title: "", install: true, uninstall: true) {

		section("<big><b>Away Switch</b></big>") {
			paragraph "The Virtual Switch that will be used to activate the Away state."

			input "awaySwitch", "capability.switch",
				title: "<b>Select Virtual Switch:</b>",
				required: true

			paragraph ""
		}

		section("<big><b>Lights To Control</b></big>") {
			paragraph "The lights that will be controlled by the Away state."

			input "lightsToControl", "capability.switch",
				title: "<b>Select Lights:</b>",
                multiple: true,
				required: true

			paragraph ""
		}

        section("<big><b>Lights On/Off Times</b></big>") {
            input "lightsOnTime", "time", title: "Time to turn on the lights", defaultValue: "18:00", required: true
            input "lightsOnAtSunset", "bool", title: "Turn lights on at sunset if earlier than 'On Time' above?", submitOnChange: true
            input "lightsOffTime", "time", title: "Time to turn off the lights", defaultValue: "22:15", required: true
        }
        
		section("<big><b>Logging</b></big>") {
			input "debugLogging", "bool",
				title: "<b>Enable debug logging?</b>",
				defaultValue: true,
				required: false
		}
	}
}

def installed() {
	log.info "Installed with settings: ${settings}"

	initialize()
}

def uninstalled() {
}

def updated() {
	log.info "Updated with settings: ${settings}"

	unsubscribe()
    unschedule()
	initialize()
}

def initialize() {
    log.info "initialize()"
    
	subscribe(awaySwitch, "switch", awaySwitchHandler)
    
    if (lightsOnAtSunset)
        subscribe(location, "sunset", sunsetHandler)

    if (lightsOnTime == null)
		onTimeOfDay = timeToday("00:00")
	else
		onTimeOfDay = timeToday(lightsOnTime, location.timeZone)
	schedule("0 ${onTimeOfDay.minutes} ${onTimeOfDay.hours} ? * *", turnLightsOn)
    
    if (lightsOffTime == null)
		offTimeOfDay = timeToday("00:00")
	else
		offTimeOfDay = timeToday(lightsOffTime, location.timeZone)
	schedule("0 ${offTimeOfDay.minutes} ${offTimeOfDay.hours} ? * *", turnLightsOff)
}

def awaySwitchHandler(e) {
    log "awaySwitchHandler(${e.value})"
    
    if (e.value == "on")
        turnLightsOn()
    else
        turnLightsOff()
}

def sunsetHandler(e) {
    log "sunsethandler(${e.value})"

    turnLightsOn(true)
}

def turnLightsOn(boolean isSunset = false) {
    log "turnLightsOn(${isSunset})"
    
    if (settings?.awaySwitch?.currentValue("switch") == "on")
    {
        def now = new Date()
        def onTimeOfDay = timeToday(lightsOnTime, location.timeZone)
        def offTimeOfDay = timeToday(lightsOffTime, location.timeZone)
        def riseAndSet = getSunriseAndSunset()
        
        log "now = ${now}"
        log "onTimeOfDay = ${onTimeOfDay}"
        log "offTimeOfDay = ${offTimeOfDay}"
        log "sunsetTimeOfDay = ${riseAndSet.sunset}"

        if (timeOfDayIsBetween(onTimeOfDay, offTimeOfDay, now, location.timeZone) || (isSunset && timeOfDayIsBetween(riseAndSet.sunset, offTimeOfDay, now, location.timeZone)))
        {
            log "Turning lights on"
            lightsToControl*.on() 
        }
        else
        {
            log "Not turning lights on due to outside of on/off time"
        }
    }
    else
    {
        log "Not turning lights on due to Away switch being off"
    }
}

def turnLightsOff() {
    log "turnLightsOff()"
    
    lightsToControl*.off()
}

def log(msg) {
	if (debugLogging) {
		log.debug(msg)	
	}
}
