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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change Log:
 *    01/17/2022 v2.0.3 - Refactored code to better handle top-down/bottom windowShade event
 *                      - Initialized setting railForLevelState if it was null
 *    01/13/2022 v2.0.2 - Fixed top-down/bottom up shade state settings for level, position and open/close
 *                      - Added preference for top-down/bottom up shade rail selection for level setting
 *                      - Refactored open(), close(), tilt_open(), tilt_close() and setTiltPosition()
 *    01/12/2022 v2.0.1 - Fixed issues with tilt capability
 *    01/06/2021 v2.0.0 - Added tilt capability based on shade capabilities
 *    11/09/2021 v1.9 - Added check for battery voltage greater than max voltage
 *    10/30/2021 v1.8 - Added check for the last time the low battery notification was sent
 *    10/26/2021 v1.7 - Added call to sendBatteryLowNotification() if batteryStatus is 1 (low)
 *    09/24/2021 v1.6 - Fixed error in battery level calculation
 *                    - Added plug-in power supply preference
 *    07/28/2021 v1.5 - Added capabilities override preference
 *    07/13/2021 v1.4 - Fixed an issue with maxVoltage set to null on driver upgrade
 *                    - Added logsOff() to updated() to automatically disable logging
 *    06/30/2021 v1.3 - Fixed Hubitat Dashboard tile colors update when open/closed/partially open
 *    04/25/2021 v1.2 - Refactored sendEvent() for battery and shade levels
 *    03/28/2021 v1.1 - Added firmware version and shade type to the Data section of Device Details
 *                    - Changed battery percentage calculation based on information from Hunter Douglas engineers
 *                    - Added "position" event for WindowShade capability
 *                    - Modified code to use the "capabilities" value of shade and added logic to handle various shade types properly
 *    05/10/2020 v1.0 - Initial release
 *
 */
metadata {
    definition(name: "Hunter Douglas PowerView Shade BETA", namespace: "hdpowerview", author: "Chris Lang", importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/hunter-douglas-powerview-shade.groovy") {
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
        command "setBottomPosition", ["number"]
        command "setTopPosition", ["number"]
        command "setTiltPosition", ["number"]
        command "tiltOpen"
        command "tiltClose"

        attribute "bottomPosition", "number"
        attribute "topPosition", "number"
        attribute "tiltPosition", "number"
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
            9: "Duolite Lift and Tilt 90°"
        ]
        
        if (getShadeCapabilities() == 7) {
            input name: 'railForLevelState', type: 'enum', title: '<b>Top Down/Bottom Up Rail for Level State</b>', description: '<div><i>Select rail that will determine the value of the level state</i></div><br>', displayDuringSetup: false, defaultValue: '0', required: false, options: [
                0: "Bottom Rail",
                1: "Top Rail",
            ]
        }
        
        input name: 'pluggedIn', type: 'bool', title: '<b>Plug-in power supply?</b>', description: '<div><i>Automatically sets battery level to 100% if enabled</i></div><br>', defaultValue: false
        input name: 'maxVoltage', type: 'decimal', title: '<b>Maximum Voltage</b>', description: '<div><i>Maximum voltage of battery wand</i></div><br>', defaultValue: '18.5', range: "1..50", required: true
        input name: 'logEnable', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 15 minutes</i></div><br>', defaultValue: true
    }
}

def installed() {
    initialize()
}

def initialize() {
    if (logEnable) runIn(900, logsOff)
    
    if(settings?.maxVoltage==null) setting?.maxVoltage=18
    
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
  .tg .tg-02ax{text-align:left;vertical-align:top;width:145px;}
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
    <td class="tg-0lax">Shades with standard bottom-up operation such as Alustra Woven Textures, Roller, Screen &amp; Banded Shades, Duette/Applause Standard, Design Studio shades, Solera, Vignette Modern Roman Shades Provenance Woven Wood Shades.</td>
  </tr>
  <tr>
    <td class="tg-baqh">1</td>
    <td class="tg-02ax">Bottom Up Tilt 90°</td>
    <td class="tg-0lax">Shades with Bottom-Up lift and 90° Tilt. Includes Pirouette, Silhouette, Silhouette A Deux Front Shade.</td>
  </tr>
  <tr>
    <td class="tg-baqh">2</td>
    <td class="tg-02ax">Bottom Up Tilt 180°</td>
    <td class="tg-0lax">Lift and Tilt Horizontal Blind (Not sold in USA at this time).</td>
  </tr>
  <tr>
    <td class="tg-baqh">3</td>
    <td class="tg-02ax">Vertical</td>
    <td class="tg-0lax">Vertically oriented shades with horizontal traverse operation only. Include Skyline Left Stack, Right Stack, Split Stack; Provenance Vertical Drapery Left Stack, Right Stack, Split Stack; Duette/Applause Vertiglide Left Stack, Right Stack.</td>
  </tr>
  <tr>
    <td class="tg-baqh">4</td>
    <td class="tg-02ax">Vertical Tilt 180°</td>
    <td class="tg-0lax">Vertically oriented shades with horizontal traverse operation plus 180° Tilt. Includes Luminette Left Stack, Right Stack and Split Stack.</td>
  </tr>
  <tr>
    <td class="tg-baqh">5</td>
    <td class="tg-02ax">Tilt Only 180°</td>
    <td class="tg-0lax">Products with tilt-only operation such as Parkland, EverWood, Modern Precious Metals and Palm Beach Shutters</td>
  </tr>
  <tr>
    <td class="tg-baqh">6</td>
    <td class="tg-0lax">Top Down</td>
    <td class="tg-0lax">Top-Down (only) operation includes Duette/Applause Top-Down.</td>
  </tr>
  <tr>
    <td class="tg-baqh">7</td>
    <td class="tg-02ax">Top Down/Bottom Up</td>
    <td class="tg-0lax">Shades with Top-Down/Bottom-Up (TDBU) operation or stacking Duolite operation including Duette/Applause TDBU, Solera TDBU, Vignette TDBU, Provenance TDBU; Alustrao Woven Textureso Romans TDBU, Duette/Applause Duolite.</td>
  </tr>
  <tr>
    <td class="tg-baqh">8</td>
    <td class="tg-02ax">Duolite Lift</td>
    <td class="tg-0lax">Shades with lift only Duolite operation eg. Vignette Duolite, Roller/Screen Duolite (not released).</td>
  </tr>
  <tr>
    <td class="tg-baqh">9</td>
    <td class="tg-02ax">Duolite Lift and Tilt 90°</td>
    <td class="tg-0lax">Duolite lift operation plus 90° tilt operation. Includes: Silhouette Duolite.</td>
  </tr>
</tbody>
</table>'''
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
    
    if(settings?.railForLevelState==null) settings?.railForLevelState=0
    
    if (shadeJson?.positions) {
        def positions = shadeJson.positions
        if (positions.posKind1) {
            updatePosition(positions.position1, positions.posKind1)
        }
        if (positions.posKind2) {
            updatePosition(positions.position2, positions.posKind2)
        }
    } else {
        // If a result doesn't include position, sometimes reissuing the poll will return it.
        if (!state?.lastPollRetry || (state?.lastPollRetry - now) > (60 * 10 * 1000)) {
            if (logEnable) log.debug "event didn't contain position, retrying poll"
            state?.lastPollRetry = now
            refresh()
        }
    }

    if (shadeJson?.batteryStrength) {
        def batteryLevel = 100
        def battVoltage = shadeJson.batteryStrength / 10

        if (logEnable) log.debug "handleEvent: battVoltage = ${battVoltage}, maxVoltage = ${settings?.maxVoltage}"
        
        if (!settings?.pluggedIn) {
            if (battVoltage < settings?.maxVoltage.toFloat()) {
                batteryLevel = (int)((battVoltage / settings?.maxVoltage.toFloat()) * 100)
            }
        }
        
        state.batteryVoltage = battVoltage
        descriptionText = "${device.displayName} battery is ${batteryLevel}%"
        sendEvent([name: "battery", value: batteryLevel, unit: "%", descriptionText: descriptionText])
    }
	
	state.batteryStatus = shadeJson?.batteryStatus;  // 0 = No Status Available, 1 = Low, 2 = Medium, 3 = High, 4 = Plugged In
    if (state.batteryStatus == 1 && (!state?.lastBatteryLowNotify || (now - state?.lastBatteryLowNotify) > (24 * 60 * 60 * 1000))) {
        state?.lastBatteryLowNotify = now
        parent.sendBatteryLowNotification(device)
    }
    
    state.capabilities = shadeJson?.capabilities;
    
    device.updateDataValue("firmwareVersion", "${shadeJson.firmware.revision}.${shadeJson.firmware.subRevision}.${shadeJson.firmware.build}")
    device.updateDataValue("shadeTypeID", "${shadeJson.type}")
}

def updatePosition(position, posKind) {
    def sendLevelEvents = true
    def eventName
    def level
    
    if (getShadeCapabilities() == 7) {
        if (logEnable) log.debug "posKind = ${posKind}, railForLevelState = ${settings?.railForLevelState}"
        
        if (posKind == 1 && settings?.railForLevelState.toInteger() == 0) { // bottom rail
            if (logEnable) log.debug "Setting level to bottom rail position"
            level = Math.round(position * 100 / 65535)
        }
        else if (posKind == 2 && settings?.railForLevelState.toInteger() == 1) { // top rail
            if (logEnable) log.debug "Setting level to top rail position"
            level = Math.round(position * 100 / 65535)
        }
        else {
            level = Math.round(position * 100 / 65535)
            sendLevelEvents = false
        }
    }
    else if (posKind == 3) {
        max = supportsTilt180() ? 65535 : 32767
        level = Math.round(position * 100 / max)
    }
    else {
        level = Math.round(position * 100 / 65535)
    }
    
    switch (posKind) {
        case 1:
            eventName = "bottomPosition"
            break
        case 2:
            eventName = "topPosition"
            break
        case 3:
            eventName = "tiltPosition"
            break
        default:
            log.error "updatePosition() unknown posKind ${posKind}"
            break
    }
    
    if (logEnable) log.debug "sending event ${eventName} with value ${level}"

    sendEvent(name: eventName, value: level)
    
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
            parent.setPosition(device, [bottomPosition: 100])
            break
        case 5:    // Tilt Only 180
            log.info "open() shade supports tilt only"
            break
        case 6:    // Top Down
            parent.setPosition(device, [topPosition: 0])
            break
        case 7:    // Top Down Bottom Up
            parent.setPosition(device, [bottomPosition: 100, topPosition: 100])
            break
        default:
            log.error "open() unknown shade capability ${shadeCapabilities}"
            break
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
            parent.setPosition(device, [bottomPosition: 0])
            break
        case 5:    // Tilt Only 180
            log.info "close() shade supports tilt only"
            break
        case 6:    // Top Down
            parent.setPosition(device, [topPosition: 100])
            break
        case 7:    // Top Down Bottom Up
            parent.setPosition(device, [bottomPosition: 0, topPosition: 0])
            break
        default:
            log.error "close() unknown shade capability ${shadeCapabilities}"
            break
    }
}

def tiltOpen() {
    if (logEnable) log.debug "tiltOpen()"
    
    if (supportsTilt()) {
        parent.setPosition(device, [tiltPosition: 100])
    }
}

def tiltClose() {
    if (logEnable) log.debug "tiltClose()"
    
    if (supportsTilt()) {
        parent.setPosition(device, [tiltPosition: 0])
    }
}

def presetPosition() {}

def calibrate() {
    parent.calibrateShade(device)
}

def jog() {
    parent.jogShade(device)
}

def setBottomPosition(bottomPosition) {
    bottomPosition = Math.min(Math.max(bottomPosition.intValue(), 0), 100)
    parent.setPosition(device, [bottomPosition: bottomPosition])
}

def setTopPosition(topPosition) {
    topPosition = Math.min(Math.max(topPosition.intValue(), 0), 100)
    parent.setPosition(device, [topPosition: topPosition])
}

def setPosition(position) {
	setLevel(position)
}

def setTiltPosition(tiltPosition) {
    if (supportsTilt()) {
        tiltPosition = Math.min(Math.max(tiltPosition.intValue(), 0), 100)
        parent.setPosition(device, [tiltPosition: tiltPosition])
    }
}

def setLevel(level, duration = null) {
    position = Math.min(Math.max(level.intValue(), 0), 100)
    parent.setPosition(device, [position: level])
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
            result = true
            break
        default:
            log.error "shade does not support tilt capability (shade capability = ${shadeCapabilities})"
            break
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
            result = true
            break
        default:
            log.error "shade does not support tilt capability (shade capability = ${shadeCapabilities})"
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

    if (getShadeCapabilities() == 7) {
        log.debug "Checking TB/BU shade closed state"
        result = (device.currentValue('bottomPosition', true) == 0 && device.currentValue('topPosition', true) == 0) ? true : false
    }
    else {
        log.debug "Checking shade closed state"
        result = (level == 0) ? true : false
    }
    
    log.debug "isClosed() = ${result}"
    
    return result
}

def isOpen(level) {
    if (logEnable) log.debug "isOpen()"
    def result

    if (getShadeCapabilities() == 7) {
        log.debug "Checking TB/BU shade open state"
        result = (device.currentValue('bottomPosition', true) == 100 && device.currentValue('topPosition', true) == 100) ? true : false
    }
    else {
        log.debug "Checking shade closed state"
        result = (level == 100) ? true : false
    }
    
    log.debug "isOpen() = ${result}"
    
    return result
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
