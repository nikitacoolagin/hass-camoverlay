"""Device discovery + MQTT command/state plumbing for CamOverlay."""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass, field

from homeassistant.components import mqtt
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_send

from .const import (
    DEFAULT_CORNER,
    DEFAULT_SIZE,
    PAYLOAD_ONLINE,
    SIGNAL_DEVICE_UPDATE,
    SIGNAL_NEW_DEVICE,
    TOPIC_AVAILABILITY,
    TOPIC_CMD,
    TOPIC_STATE,
)

_LOGGER = logging.getLogger(__name__)


@dataclass
class DeviceData:
    """Live state for one CamOverlay device (e.g. tv, phone)."""

    name: str
    available: bool = False
    state: str = "unknown"
    # locally-held PiP selection used by the "show PiP" button
    size: str = DEFAULT_SIZE
    corner: str = DEFAULT_CORNER


class CamOverlayCoordinator:
    """Subscribes to the broker, tracks devices, and publishes commands."""

    def __init__(self, hass: HomeAssistant, prefix: str) -> None:
        self.hass = hass
        self.prefix = prefix
        self.devices: dict[str, DeviceData] = {}
        self._unsubscribes: list = []

    async def async_start(self) -> None:
        """Subscribe to availability + state for every device under the prefix."""
        self._unsubscribes.append(
            await mqtt.async_subscribe(
                self.hass, f"{self.prefix}/+/{TOPIC_AVAILABILITY}", self._on_availability
            )
        )
        self._unsubscribes.append(
            await mqtt.async_subscribe(
                self.hass, f"{self.prefix}/+/{TOPIC_STATE}", self._on_state
            )
        )

    async def async_stop(self) -> None:
        for unsub in self._unsubscribes:
            unsub()
        self._unsubscribes.clear()

    def _device_from_topic(self, topic: str) -> str | None:
        # <prefix>/<device>/<leaf>
        parts = topic.split("/")
        if len(parts) != 3 or parts[0] != self.prefix:
            return None
        return parts[1]

    @callback
    def _ensure_device(self, name: str) -> tuple[DeviceData, bool]:
        is_new = name not in self.devices
        if is_new:
            self.devices[name] = DeviceData(name=name)
        return self.devices[name], is_new

    @callback
    def _on_availability(self, msg) -> None:
        name = self._device_from_topic(msg.topic)
        if name is None:
            return
        device, is_new = self._ensure_device(name)
        device.available = msg.payload.strip() == PAYLOAD_ONLINE
        self._notify(name, is_new)

    @callback
    def _on_state(self, msg) -> None:
        name = self._device_from_topic(msg.topic)
        if name is None:
            return
        device, is_new = self._ensure_device(name)
        device.state = msg.payload.strip() or "unknown"
        self._notify(name, is_new)

    @callback
    def _notify(self, name: str, is_new: bool) -> None:
        if is_new:
            _LOGGER.debug("Discovered CamOverlay device: %s", name)
            async_dispatcher_send(self.hass, SIGNAL_NEW_DEVICE, name)
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_UPDATE.format(name))

    async def async_publish(self, device: str, payload: dict) -> None:
        topic = f"{self.prefix}/{device}/{TOPIC_CMD}"
        await mqtt.async_publish(self.hass, topic, json.dumps(payload), qos=0, retain=False)

    async def async_fullscreen(self, device: str) -> None:
        await self.async_publish(device, {"action": "full"})

    async def async_stop_cmd(self, device: str) -> None:
        await self.async_publish(device, {"action": "stop"})

    async def async_pip(self, device: str, size: str, corner: str) -> None:
        await self.async_publish(
            device, {"action": "pip", "size": size, "corner": corner}
        )
