"""Buttons per CamOverlay device: fullscreen, show PiP, stop."""
from __future__ import annotations

from homeassistant.components.button import ButtonEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .const import DOMAIN, SIGNAL_NEW_DEVICE
from .coordinator import CamOverlayCoordinator
from .entity import CamOverlayEntity


async def async_setup_entry(
    hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback
) -> None:
    coordinator: CamOverlayCoordinator = hass.data[DOMAIN][entry.entry_id]

    @callback
    def _add(device: str) -> None:
        async_add_entities(
            [
                CamOverlayFullscreenButton(coordinator, device),
                CamOverlayPipButton(coordinator, device),
                CamOverlayStopButton(coordinator, device),
            ]
        )

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_NEW_DEVICE, _add))
    for device in list(coordinator.devices):
        _add(device)


class _BaseButton(CamOverlayEntity, ButtonEntity):
    _suffix = ""

    def __init__(self, coordinator: CamOverlayCoordinator, device: str) -> None:
        super().__init__(coordinator, device)
        self._attr_unique_id = f"{coordinator.prefix}_{device}_{self._suffix}"

    @property
    def available(self) -> bool:
        data = self._data
        return bool(data and data.available)


class CamOverlayFullscreenButton(_BaseButton):
    _attr_name = "Fullscreen"
    _attr_icon = "mdi:fullscreen"
    _suffix = "fullscreen"

    async def async_press(self) -> None:
        await self.coordinator.async_fullscreen(self.device)


class CamOverlayStopButton(_BaseButton):
    _attr_name = "Stop"
    _attr_icon = "mdi:close-box"
    _suffix = "stop"

    async def async_press(self) -> None:
        await self.coordinator.async_stop_cmd(self.device)


class CamOverlayPipButton(_BaseButton):
    _attr_name = "Show PiP"
    _attr_icon = "mdi:picture-in-picture-bottom-right"
    _suffix = "pip"

    async def async_press(self) -> None:
        data = self._data
        size = data.size if data else "quarter"
        corner = data.corner if data else "br"
        await self.coordinator.async_pip(self.device, size, corner)
