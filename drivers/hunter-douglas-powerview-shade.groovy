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
    definition(name: "Hunter Douglas PowerView Shade", namespace: "hdpowerview", author: "Chris Lang", importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/drivers/hunter-douglas-powerview-shade.groovy") {
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

        attribute "bottomPosition", "number"
        attribute "topPosition", "number"
    }

    preferences {
        input name: 'maxVoltage', type: 'number', title: '<b>Maximum Voltage</b>', description: '<div><i>Maximum voltage of battery wand</i></div><br>', defaultValue: '18'
        input name: 'logEnable', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 15 minutes.</i></div><br>', defaultValue: true
    }
}

def installed() {
    initialize()
}

def initialize() {
    if (logEnable) runIn(900, logsOff)
    
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
    <td class="tg-02ax">Top Down Bottom Up</td>
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

def refresh() {
    parent?.pollShade(device)
}

public handleEvent(shadeJson) {
    if (logEnable) log.debug "handleEvent: shadeJson = ${shadeJson}"
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
        def now = now()
        if (!state?.lastPollRetry || (state?.lastPollRetry - now) > (60 * 10 * 1000)) {
            if (logEnable) log.debug "event didn't contain position, retrying poll"
            state?.lastPollRetry = now
            refresh()
        }
    }

    if (shadeJson?.batteryStrength) {
        def battVoltage = shadeJson.batteryStrength / 10
        def batteryLevel = (int)((battVoltage / maxVoltage) * 100)
        
        state.batteryVoltage = battVoltage
        descriptionText = "${device.displayName} battery is ${batteryLevel}%"
        sendEvent([name: "battery", value: batteryLevel, unit: "%", descriptionText: descriptionText])
    }
	
	state.batteryStatus = shadeJson.batteryStatus;  // 0 = No Status Available, 1 = Low, 2 = Medium, 3 = High, 4 = Plugged In
    state.capabilities = shadeJson.capabilities;
    
    device.updateDataValue("firmwareVersion", "${shadeJson.firmware.revision}.${shadeJson.firmware.subRevision}.${shadeJson.firmware.build}")
    device.updateDataValue("shadeTypeID", "${shadeJson.type}")
}

def updatePosition(position, posKind) {
    def level = (int)(position * 100 / 65535)
    def eventName = (posKind == 1) ? "bottomPosition" : "topPosition"
    if (logEnable) log.debug "sending event ${eventName} with value ${level}"

    sendEvent(name: eventName, value: level)
    sendEvent(name: "level", value: level)
    sendEvent(name: "position", value: level)

    if (level > 0 && level < 99) {
		sendEvent(name: "windowShade", value: "partially open", displayed:true)
        sendEvent(name: "switch", value: "on")
	}
	else if (level >= 99) {
		sendEvent(name: "windowShade", value: "open", displayed:true)
        sendEvent(name: "switch", value: "on")
	}
	else {
		sendEvent(name: "windowShade", value: "closed", displayed:true)
        sendEvent(name: "switch", value: "off")
	}
}

// parse events into attributes
def parse(String description) {}

// handle commands
def open() {
    if (logEnable) log.debug "Executing 'open'"
    
    switch (state.capabilities) {
        case 0:    // Bottom Up
            parent.setPosition(device, [bottomPosition: 100])
            break
        case 3:    // Vertically oriented
            parent.setPosition(device, [bottomPosition: 100])
            break        
        case 6:    // Top Down
            parent.setPosition(device, [topPosition: 0])
            break
        case 7:    // Top Down Bottom Up
            parent.setPosition(device, [bottomPosition: 100, topPosition: 0])
            break
        default:
            break
    }
}

def close() {
    if (logEnable) log.debug "Executing 'close'"
    
    switch (state.capabilities) {
        case 0:    // Botton Up
            parent.setPosition(device, [bottomPosition: 0])
            break
        case 3:    // Vertically oriented
            parent.setPosition(device, [bottomPosition: 0])
            break
        case 6:    // Top Down
            parent.setPosition(device, [topPosition: 100])
            break
        case 7:    // Top Down Bottom Up
            parent.setPosition(device, [bottomPosition: 0, topPosition: 0])
            break
        default:
            break
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

def setLevel(level, duration = null) {
    position = Math.min(Math.max(level.intValue(), 0), 100)
    parent.setPosition(device, [position: level])
}

def logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
