// main index.js

import { NativeModules } from 'react-native';

const { HoneywellRfidIh25 } = NativeModules;

const events = {};

const eventEmitter = new NativeEventEmitter(HoneywellRfidIh25);

HoneywellRfidIh25.on = (event, handler) => {
	const eventListener = eventEmitter.addListener(event, handler);

	events[event] =  events[event] ? [...events[event], eventListener]: [eventListener];
};

HoneywellRfidIh25.off = (event) => {
	if (Object.hasOwnProperty.call(events, event)) {
		const eventListener = events[event].shift();

		if(eventListener) eventListener.remove();
	}
};

HoneywellRfidIh25.removeAll = (event) => {
	if (Object.hasOwnProperty.call(events, event)) {
		eventEmitter.removeAllListeners(event);

		events[event] = [];
	}
}

export default HoneywellRfidIh25;
