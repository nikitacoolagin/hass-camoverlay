"""Connectivity binary sensor per CamOverlay device."""
from __future__ import annotations

from homeassistant.components.binary_sensor import (
    BinarySensorDeviceClass,
    BinarySensorEntity,
)
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
        async_add_entities([CamOverlayOnline(coordinator, device)])

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_NEW_DEVICE, _add))
    for device in list(coordinator.devices):
        _add(device)


class CamOverlayOnline(CamOverlayEntity, BinarySensorEntity):
    _attr_device_class = BinarySensorDeviceClass.CONNECTIVITY
    _attr_name = "Online"

    def __init__(self, coordinator: CamOverlayCoordinator, device: str) -> None:
        super().__init__(coordinator, device)
        self._attr_unique_id = f"{coordinator.prefix}_{device}_online"

    @property
    def is_on(self) -> bool:
        data = self._data
        return bool(data and data.available)
