/**
 *  Hunter Douglas PowerView Shade
 *
 *  Copyright 2017 Chris Lang
 *
 *  Ported to Hubitat by Brian Ujvary
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0/
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change Log:
 *    11/04/2023 v0.13 - Replaced all exceptions with log.debug
 *    04/12/2023 v0.12 - Adjusted tilt logic event notification in updatePosition()
 *    04/07/2023 v0.11 - Added null checks in updatePosition()
 *    03/31/2023 v0.10 - Added battery level event in handleEvent()
 *    12/08/2022 v0.9 - Version number update only
 *    12/08/2022 v0.8 - Version number update only
 *    12/08/2022 v0.7 - Version number update only
 *    12/08/2022 v0.6 - Version number update only
 *    10/07/2022 v0.5 - Added logic to convert shade type to an equivalent shade capabilities value
 *                    - Added logic to reinitialize the "Shade capability information" state on install
 *    10/06/2022 v0.4 - Fixed position type for Top-Down shades in open() and close()
 *                    - Initialized state.capabilities to -1 if not in shade data from gateway
 *                    - Added note to "Shade capability information" about state.capabilities being -1
 *                    - Fixed checks in handleEvent() for missing values
 *                    - Added logic to isOpen() and isClosed() to check state of Top-Down shades
 *    10/04/2022 v0.3 - Version number update only
 *    10/04/2022 v0.2 - Version number update only
 *    10/03/2022 v0.1 - Initial release
 *
 */
metadata {
    definition(name: "Hunter Douglas PowerView Shade Gen3", namespace: "hdpowerview", author: "Chris Lang", importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/hunter-douglas-powerview-shade-gen3.groovy") {
        capability "Initialize"
        capability "Actuator"
        capability "Battery"
        capability "Refresh"
        capability "Sensor"
        capability "WindowShade"
        capability "Switch"
		capability "SwitchLevel"

        command "calibrate"
        command "jog"
        command "stop"
        command "presetPosition"
        command "setPrimaryPosition", ["number"]
        command "setSecondaryPosition", ["number"]
        command "setTiltPosition", ["number"]
        command "tiltOpen"
        command "tiltClose"
        command "tiltClockwise"
        command "tiltCounterClockwise"

        attribute "primaryPosition", "number"
        attribute "secondaryPosition", "number"
        attribute "tiltPosition", "number"
        attribute "refreshTimedOut", "enum", ["true", "false"]
    }

    preferences {
        input name: 'capabilityOverride', type: 'enum', title: '<b>Shade Capability Override</b>', description: '<div><i>Only set this if your shade capabilities value is -1. Use the table above to select the correct shade capability.</i></div><br>', displayDuringSetup: false, required: false, options: [
            0: "Bottom Up",
            1: "Bottom Up Tilt 90°",
            2: "Bottom Up Tilt 180°",
            3: "Vertical",
            4: "Vertical Tilt 180°",
            5: "Tilt Only 180°",
            6: "Top Down",
            7: "Top Down/Bottom Up",
            8: "Duolite Lift",
            9: "Duolite Lift and Tilt 90°",
            10: "Duolite Lift and Tilt 180°"
        ]
        
        if (getShadeCapabilities() == 7) {
            input name: 'railForLevelState', type: 'enum', title: '<b>Top Down/Bottom Up Rail for Level State</b>', description: '<div><i>Select rail that will determine the value of the level state</i></div><br>', displayDuringSetup: false, defaultValue: '0', required: false, options: [
                0: "Bottom Rail",
                1: "Top Rail",
            ]
        }

        input name: 'preset', type: 'number', title: '<b>Preset Position</b>', description: '<div><i>Set the window shade preset position (defaults to 50)</i></div><br>', displayDuringSetup: false, defaultValue: 50, range: "0..100"
        input name: 'logEnable', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 15 minutes</i></div><br>', displayDuringSetup: false, defaultValue: true
    }
}

def installed() {
    if (state."Shade capability information from Hunter Douglas" != null)
        state.remove("Shade capability information from Hunter Douglas")
    
    initialize()
}

def initialize() {
    if (logEnable) runIn(900, logsOff)
    
    //state.remove("Shade capability information from Hunter Douglas")
    if (!state."Shade capability information from Hunter Douglas") {
      state."Shade capability information from Hunter Douglas" = '''
<style type="text/css">
  .tg  {border-collapse:collapse;border-color:#ccc;border-spacing:0;font-weight:normal;}
  .tg td{background-color:#fff;border-color:#ccc;border-style:solid;border-width:1px;color:#333;
    font-family:Helvetica,Arial,sans-serif;font-size:14px;overflow:hidden;padding:4px 6px;word-break:normal;}
  .tg th{background-color:#f0f0f0;border-color:#ccc;border-style:solid;border-width:1px;color:#333;
    font-family:Helvetica,Arial,sans-serif;font-size:14px;overflow:hidden;padding:4px 6px;word-break:normal;}
  .tg .tg-1wig{font-weight:bold;text-align:left;vertical-align:top;}
  .tg .tg-baqh{text-align:center;vertical-align:top;width:110px;}
  .tg .tg-02ax{text-align:left;vertical-align:top;width:165px;}
  .tg .tg-01ax{text-align:left;vertical-align:top;}
</style>

<table class="tg">
<thead>
  <tr>
    <th class="tg-1wig">Capability Value</th>
    <th class="tg-1wig">Shade Type</th>
    <th class="tg-1wig">Shade Description</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td class="tg-baqh">0</td>
    <td class="tg-02ax">Bottom Up</td>
    <td class="tg-0lax">Shades with standard bottom-up operation. Includes Standard roller/screen shades, Duette bottom up.</br>&nbsp;&bull;&nbsp;Uses the “primary” control type</td>
  </tr>
  <tr>
    <td class="tg-baqh">1</td>
    <td class="tg-02ax">Bottom Up w/ Tilt 90°</td>
    <td class="tg-0lax">Shades with Bottom-Up lift and 90° Tilt. Includes Silhouette, Pirouette.</br>&nbsp;&bull;&nbsp;Uses the “primary” and “tilt” control types</td>
  </tr>
  <tr>
    <td class="tg-baqh">2</td>
    <td class="tg-02ax">Bottom Up w/ Tilt 180°</td>
    <td class="tg-0lax">Lift and Tilt Horizontal Blind. Includes Silhouette Halo</br>&nbsp;&bull;&nbsp;Uses the “primary” and “tilt” control types</td>
  </tr>
  <tr>
    <td class="tg-baqh">3</td>
    <td class="tg-02ax">Vertical (Traversing)</td>
    <td class="tg-0lax">Vertically oriented shades with horizontal traverse operation only. Includes Skyline, Duette Vertiglide, Design Studio Drapery.</br>&nbsp;&bull;&nbsp;Uses the “primary” control type</td>
  </tr>
  <tr>
    <td class="tg-baqh">4</td>
    <td class="tg-02ax">Vertical (Traversing) w/ Tilt 180°</td>
    <td class="tg-0lax">Vertically oriented shades with horizontal traverse operation plus 180° Tilt. Includes Luminette.</br>&nbsp;&bull;&nbsp;Uses the “primary” and “tilt” control types</td>
  </tr>
  <tr>
    <td class="tg-baqh">5</td>
    <td class="tg-02ax">Tilt Only 180°</td>
    <td class="tg-0lax">Products with tilt-only operation. Includes Palm Beach Shutters, Parkland Wood Blinds</br>&nbsp;&bull;&nbsp;Uses the “tilt” control type</td>
  </tr>
  <tr>
    <td class="tg-baqh">6</td>
    <td class="tg-0lax">Top Down</td>
    <td class="tg-0lax">Top-Down (only) operation. Includes Duette/Applause Top-Down.</br>&nbsp;&bull;&nbsp;Uses the “primary” control type</td>
  </tr>
  <tr>
    <td class="tg-baqh">7</td>
    <td class="tg-02ax">Top Down/Bottom Up</td>
    <td class="tg-0lax">Shades with Top-Down/Bottom-Up (TDBU) operation or stacking Duolite operation. Includes Duette/Applause TDBU, Solera TDBU, Vignette TDBU, Provenance TDBU; Alustrao Woven Textureso Romans TDBU, Duette/Applause Duolite.</br>&nbsp;&bull;&nbsp;Uses the “primary” and “secondary” control types</td>
  </tr>
  <tr>
    <td class="tg-baqh">8</td>
    <td class="tg-02ax">Duolite Lift</td>
    <td class="tg-0lax">Shades with lift only Duolite operation. Includes Roller Duolite, Vignette Duolite, Dual Roller.</br>&nbsp;&bull;&nbsp;Uses the “primary” and “secondary” control types</td>
  </tr>
  <tr>
    <td class="tg-baqh">9</td>
    <td class="tg-02ax">Duolite Lift with Tilt 90°</td>
    <td class="tg-0lax">Duolite lift operation plus 90° tilt operation. Includes Silhouette Duolite, Silhouette Adeux.</br>&nbsp;&bull;&nbsp;Uses the “primary,” “secondary,” and “tilt” control types</td>
  </tr>
  <tr>
    <td class="tg-baqh">10</td>
    <td class="tg-02ax">Duolite Lift with Tilt 180°</td>
    <td class="tg-0lax">Duolite lift operation plus 180° tilt operation. Includes Silhouette Halo Duolite.</br>&nbsp;&bull;&nbsp;Uses the “primary,” “secondary,” and “tilt” control types</td>
  </tr>
</tbody>
</table>
</br>
Note: If the shade capabilities value is -1 then you will need to set the Shade Capability Override value in the Preferences section.'''
    }
}

def updated() {
    if (logEnable) runIn(900, logsOff)
}

def refresh() {
    parent?.pollShade(device)
}

public handleEvent(shadeJson) {
    if (logEnable) log.debug "handleEvent: shadeJson = ${shadeJson}"
    def now = now()
    
    if(settings?.railForLevelState==null)
        settings?.railForLevelState=0
    
    if (shadeJson?.positions != null) {
        updatePosition(shadeJson.positions)
    }
	
    if (shadeJson?.batteryStatus != null) {
        def batteryLevel
        
	    state.batteryStatus = shadeJson?.batteryStatus;  // 0 = minimum, 3 = maximum

        switch (shadeJson?.batteryStatus) {
            case 1:  // Low
                batteryLevel = 10
                break
            case 2:  // Medium
                batteryLevel = 50
                break
            case 3:  // High
            case 4:  // Plugged in
                batteryLevel = 100
                break
            default:
                batteryLevel = unknown
                break
        }
        
        descriptionText = "${device.displayName} battery is ${batteryLevel}%"
        sendEvent([name: "battery", value: batteryLevel, unit: "%", descriptionText: descriptionText])
        
        if (shadeJson?.batteryStatus == 1 && (!state?.lastBatteryLowNotify || (now - state?.lastBatteryLowNotify) > (24 * 60 * 60 * 1000))) {
            state?.lastBatteryLowNotify = now
            parent.sendBatteryLowNotification(device)
        }
    }
    
    if (shadeJson?.capabilities != null) {
        state.capabilities = shadeJson.capabilities;
    }
    else {
        if (state.capabilities == null) {
            state.capabilities = shadeTypeToCapability(shadeJson.type)
        }
    }
    
    if (shadeJson?.firmware != null) {
        device.updateDataValue("firmwareVersion", "${shadeJson.firmware.revision}.${shadeJson.firmware.subRevision}.${shadeJson.firmware.build}")
    }
    
    device.updateDataValue("shadeTypeID", "${shadeJson.type}")
}

public handleSseEvent(shadeJson) {
    if (logEnable) log.debug "handleSseEvent: shadeJson = ${shadeJson}"
    
    if (shadeJson?.currentPositions != null) {
        updatePosition(shadeJson.currentPositions)
    }
    else {
        log.error "Shade Event not valid. Missing currentPositions array."
    }
}

def updatePosition(positions) {
    def sendLevelEvents = false
    def posKind = primary
    def eventName
    def level
    def tilt

    if (supportsSecondary() && positions?.secondary != null) {
        level = Math.round(positions.secondary * 100)
        sendEvent(name: "secondaryPosition", value: level)
    }

    if (supportsPrimary() && positions?.primary != null) {
        level = Math.round(positions.primary * 100)
        sendEvent(name: "primaryPosition", value: level)
        sendLevelEvents = true
    }
    
    if (supportsTilt() && positions?.tilt != null) {
        tilt = Math.round(positions.tilt * 100)
        sendEvent(name: "tiltPosition", value: tilt)
        
        if (getShadeCapabilities() == 5) {
            level = tilt
            sendLevelEvents = true
        }
    }
    
    if (getShadeCapabilities() == 7) {
        if (logEnable) log.debug "railForLevelState = ${settings?.railForLevelState}"
        
        if (positions?.primary != null && settings?.railForLevelState.toInteger() == 0) { // bottom rail
            if (logEnable) log.debug "Setting level to bottom rail position"
            level = Math.round(positions.primary * 100)
            sendLevelEvents = true
        }
        else if (positions?.secondary != null && settings?.railForLevelState.toInteger() == 1) { // top rail
            if (logEnable) log.debug "Setting level to top rail position"
            level = Math.round(positions.secondary * 100)
            sendLevelEvents = true
        }
    }

    if (sendLevelEvents) {
        sendEvent(name: "level", value: level)
        sendEvent(name: "position", value: level)
    }
    
    runIn(1, updateWindowShadeState)
}

def updateWindowShadeState() {
    if (logEnable) log.debug "updateWindowShadeState()"
    
    level = device.currentValue('level', true)

    if (isOpen(level)) {
        sendEvent(name: "windowShade", value: "open", displayed:true)
        sendEvent(name: "switch", value: "on")
    }
    else if (isClosed(level)) {
        sendEvent(name: "windowShade", value: "closed", displayed:true)
        sendEvent(name: "switch", value: "off")  
    }
    else {
        sendEvent(name: "windowShade", value: "partially open", displayed:true)
        sendEvent(name: "switch", value: "on")
    }
}

// parse events into attributes
def parse(String description) {}

// handle commands
def open() {
    if (logEnable) log.debug "open()"
    
    def shadeCapabilities = getShadeCapabilities()

    switch (shadeCapabilities) {
        case 0:    // Bottom Up
        case 1:    // Bottom Up Tilt 90
        case 2:    // Bottom Up Tilt 180
        case 3:    // Vertical
        case 4:    // Vertical Tilt 180
        case 8:    // Duolite Lift
        case 9:    // Duolite Lift and Tilt 90
        case 10:   // Duolite Lift and Tilt 180
            parent.setPosition(device, [primary: 100])
            break
        case 5:    // Tilt Only 180
            log.info "open() shade supports tilt only"
            break
        case 6:    // Top Down
            parent.setPosition(device, [primary: 0])
            break
        case 7:    // Top Down Bottom Up
            parent.setPosition(device, [primary: 100, secondary: 0])
            break
        default:
            if (logEnable) log.debug "Unknown shade capability \"${shadeCapabilities}\""
    }
}

def close() {
    if (logEnable) log.debug "close()"
    
    def shadeCapabilities = getShadeCapabilities()

    switch (shadeCapabilities) {
        case 0:    // Botton Up
        case 1:    // Bottom Up Tilt 90
        case 2:    // Bottom Up Tilt 180
        case 3:    // Vertical
        case 4:    // Vertical Tilt 180
        case 8:    // Duolite Lift
        case 9:    // Duolite Lift and Tilt 90
        case 10:   // Duolite Lift and Tilt 180
            parent.setPosition(device, [primary: 0])
            break
        case 5:    // Tilt Only 180
            log.info "close() shade supports tilt only"
            break
        case 6:    // Top Down
            parent.setPosition(device, [primary: 100])
            break
        case 7:    // Top Down Bottom Up
            parent.setPosition(device, [primary: 0, secondary: 0])
            break
        default:
            if (logEnable) log.debug "Unknown shade capability \"${shadeCapabilities}\""
    }
}

def on() {
    open()
}

def off() {
    close()
}

def tiltOpen() {
    if (logEnable) log.debug "tiltOpen()"
    
    if (supportsTilt()) {
        if(supportsTilt180())
            parent.setPosition(device, [tilt: 50])
        else
            parent.setPosition(device, [tilt: 100])
    }
}

def tiltClose() {
    if (logEnable) log.debug "tiltClose()"
    
    if (supportsTilt()) {
        parent.setPosition(device, [tilt: 0])
    }
}

def tiltClockwise() {
    if (logEnable) log.debug "tiltClockwise()"
    
    if (supportsTilt()) {
        parent.setPosition(device, [tilt: 100])
    }
}

def tiltCounterClockwise() {
    if (logEnable) log.debug "tiltCounterClockwise()"
    
    tiltClose()
}

def presetPosition() {
	setLevel(settings?.preset ?: 50)
}

def calibrate() {
    parent.calibrateShade(device)
}

def jog() {
    parent.jogShade(device)
}

def stop() {
    parent.stopShade(device)
}

def setPrimaryPosition(primaryPosition) {
    primaryPosition = Math.min(Math.max(primaryPosition.intValue(), 0), 100)
    parent.setPosition(device, [primary: primaryPosition])
}

def setSecondaryPosition(secondaryPosition) {
    secondaryPosition = Math.min(Math.max(secondaryPosition.intValue(), 0), 100)
    parent.setPosition(device, [secondary: secondaryPosition])
}

def setPosition(position) {
	setLevel(position)
}

def setTiltPosition(tiltPosition) {
    if (supportsTilt()) {
        tiltPosition = Math.min(Math.max(tiltPosition.intValue(), 0), 100)
        parent.setPosition(device, [tilt: tiltPosition])
    }
}

def setLevel(position, duration = null) {
    if (logEnable) log.debug "setLevel(): ${position}"
    def positions = [:]
    
    position = Math.min(Math.max(position.intValue(), 0), 100)

    try {
        if (getShadeCapabilities() == 7 && device.currentValue('secondaryPosition', true) != 0) {
            // The top rail is not closed but the bottom was told to move.  In nearly all
            // cases, this should mean the top closes.
            positions = [secondary: 0, primary: position]
        } else {
            if (supportsPrimary()) {
                positions = [primary: position]
            }
        }
        
        parent.setPosition(device, positions)
    } catch (err) {
        log.error "Unable to set level: ${err}" 
    }
}

def startPositionChange(direction) {
    if (logEnable) log.debug "startPositionChange(): ${direction}"

	switch (direction) {
		case "close":
			close()
            break
		case "open":
			open()
            break
		default:
            if (logEnable) log.debug "Unsupported startPositionChange direction \"${direction}\""
            break
	}
}

def stopPositionChange() {
	if (logEnable) log.debug "stopPositionChange()"
	stop()
}

def supportsPrimary() {
    if (logEnable) log.debug "supportsPrimary()"

    def shadeCapabilities = getShadeCapabilities()
    def result = false
    
    switch (shadeCapabilities) {
        case 0:    // Botton Up
        case 1:    // Bottom Up Tilt 90
        case 2:    // Bottom Up Tilt 180
        case 3:    // Vertical
        case 4:    // Vertical Tilt 180
        case 6:    // Top Down
        case 7:    // Top Down Bottom Up
        case 8:    // Duolite Lift
        case 9:    // Duolite Lift and Tilt 90
        case 10:   // Duolite Lift and Tilt 180
            result = true
            break
        default:
            if (logEnable) log.debug "Shade does not support primary control type (shade capability = ${shadeCapabilities})"
    }
    
    return result
}

def supportsSecondary() {
    if (logEnable) log.debug "supportsSecondary()"

    def shadeCapabilities = getShadeCapabilities()
    def result = false
    
    switch (shadeCapabilities) {

        case 7:    // Top Down Bottom Up
        case 8:    // Duolite Lift
        case 9:    // Duolite Lift and Tilt 90
        case 10:   // Duolite Lift and Tilt 180
            result = true
            break
        default:
            if (logEnable) log.debug "Shade does not support secondary control type (shade capability = ${shadeCapabilities})"
    }
    
    return result
}

def supportsTilt() {
    if (logEnable) log.debug "supportsTilt()"
    
    def shadeCapabilities = getShadeCapabilities()
    def result = false
    
    switch (shadeCapabilities) {
        case 1:    // Bottom Up Tilt 90
        case 2:    // Bottom Up Tilt 180
        case 4:    // Vertical Tilt 180
        case 5:    // Tilt Only 180
        case 9:    // Duolite Lift and Tilt 90
        case 10:   // Duolite Lift and Tilt 180
            result = true
            break
        default:
            if (logEnable) log.debug "Shade does not support tilt control type (shade capability = ${shadeCapabilities})"
            break
    }
    
    return result
}

def supportsTilt180() {
    if (logEnable) log.debug "supportsTilt180()"
    
    def shadeCapabilities = getShadeCapabilities()
    def result = false
    
    switch (shadeCapabilities) {
        case 1:    // Bottom Up Tilt 90
        case 9:    // Duolite Lift and Tilt 90
            result = false
            break
        case 2:    // Bottom Up Tilt 180
        case 4:    // Vertical Tilt 180
        case 5:    // Tilt Only 180
        case 10:   // Duolite Lift and Tilt 180
            result = true
            break
        default:
            if (logEnable) log.debug "Shade does not support tilt control type (shade capability = ${shadeCapabilities})"
            break
    }
    
    return result
}

def getShadeCapabilities() {
    return (capabilityOverride == null) ? state?.capabilities : capabilityOverride.toInteger()    
}

def isClosed(level) {
    if (logEnable) log.debug "isClosed()"
    def result
    def shadeCapabilities = getShadeCapabilities()
    
    if (shadeCapabilities == 6) {
        if (logEnable) log.debug "Checking Top-Down shade closed state"
        result = (level >= 99) ? true : false        
    }
    else if (shadeCapabilities == 7) {
        if (logEnable) log.debug "Checking TB/BU shade closed state"
        result = (device.currentValue('primaryPosition', true) == 0 && device.currentValue('secondaryPosition', true) == 0) ? true : false
    }
    else {
        if (logEnable) log.debug "Checking shade closed state"
        result = (level <= 1) ? true : false
    }
    
    if (logEnable) log.debug "isClosed() = ${result}"
    
    return result
}

def isOpen(level) {
    if (logEnable) log.debug "isOpen()"
    def result
    def shadeCapabilities = getShadeCapabilities()
    
    if (shadeCapabilities == 6) {
        if (logEnable) log.debug "Checking Top-Down shade open state"
        result = (level <= 1) ? true : false        
    }
    else if (shadeCapabilities == 7) {
        if (logEnable) log.debug "Checking TB/BU shade open state"
        result = (device.currentValue('primaryPosition', true) == 100 && device.currentValue('secondaryPosition', true) == 0) ? true : false
    }
    else {
        if (logEnable) log.debug "Checking shade open state"
        result = (level >= 99) ? true : false
    }
    
    if (logEnable) log.debug "isOpen() = ${result}"
    
    return result
}

def shadeTypeToCapability(shadeType) {
    if (logEnable) log.debug "shadeTypeToCapability()"
    
    def capability = -1
    
    switch (shadeType) {
        case 1:
        case 4:
        case 5:
        case 6:
        case 31:
        case 42:
        case 49:
            capability = 0
            break
        case 18:
        case 23:
        case 43:
        case 44:
            capability = 1
            break
        case 51:
        case 62:
            capability = 2
            break
        case 69:
        case 70:
        case 71:
            capability = 3
            break
        case 54:
        case 55:
        case 56:
            capability = 4
            break
        case 66:
            capability = 5
            break
        case 7:
            capability = 6
            break
        case 8:
        case 9:
        case 33:
        case 47:
            capability = 7
            break
        case 65:
        case 79:
            capability = 8
            break
        case 38:
            capability = 9
            break
        default:
            log.error("Unknown shade type ${shadeType})")
            break
    }
    
    if (logEnable) log.debug "shadeTypeToCapability() = ${capability}"
    
    return capability    
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
