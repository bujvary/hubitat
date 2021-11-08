/*
 *  Vehcile Remote Starter (Apps Code)
 *
 *  Changelog:
 *
 *    1.0 (10/20/2021)
 *      - Initial Release
 *
 *
 *  Copyright 2021 Brian Ujvary
 *
 *  This app is based on the work of Kevin LaFramboise - Zooz Garage Door Opener for Hubitat
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
	name: "Vehicle Remote Starter",
    namespace: "bujvary",
    author: "Brian Ujvary",
    description: "Control your vehicle with the Zooz MultiRelay device and a remote starter in the vehicle.",
	singleInstance: true,	
	category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/images/car.png",
    iconX2Url: "https://raw.githubusercontent.com/bujvary/hubitat/master/images/car.png",
    iconX3Url: "https://raw.githubusercontent.com/bujvary/hubitat/master/images/car.png",
	installOnOpen: true,
	importUrl: "https://raw.githubusercontent.com/bujvary/hubitat/master/apps/vehicle-remote-starter.groovy"
)


preferences {
	page(name:"pageMain")
	page(name:"pageRemove")
}

def pageMain() {	
	dynamicPage(name: "pageMain", title: "", install: true, uninstall: false) {
		section() {
			paragraph "Created for use with the Zooz Universal Relay ZEN17 but can be used with any smart relay"
		}
		section() {
			app(name: "vehicles", title: "Create a Vehicle", appName: "Vehicle Remote Starter App", namespace: "bujvary", multiple: true, uninstall: false)
		}
		
		if (state.installed) {
			section() {
				href "pageRemove", title: "Remove All Vehicles", description: ""
			}
		}
	}	
}

def pageRemove() {
	dynamicPage(name: "pageRemove", title: "", install: false, uninstall: true) {
		section() {			
			paragraph "<b>WARNING:</b> You are about to remove the Vehicle Remote Starter App and ALL of the Vehicle devices it created.", required: true, state: null
		}
	}
}

def installed() {
	log.warn "installed()..."
	state.installed = true
}

def updated() {		
	log.warn "updated()..."
}
