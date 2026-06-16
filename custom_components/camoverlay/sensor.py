"""State sensor per CamOverlay device (idle / full / pip:size:corner)."""
from __future__ import annotations

from homeassistant.components.sensor import SensorEntity
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
        async_add_entities([CamOverlayState(coordinator, device)])

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_NEW_DEVICE, _add))
    for device in list(coordinator.devices):
        _add(device)


class CamOverlayState(CamOverlayEntity, SensorEntity):
    _attr_name = "State"
    _attr_icon = "mdi:overscan"

    def __init__(self, coordinator: CamOverlayCoordinator, device: str) -> None:
        super().__init__(coordinator, device)
        self._attr_unique_id = f"{coordinator.prefix}_{device}_state"

    @property
    def available(self) -> bool:
        data = self._data
        return bool(data and data.available)

    @property
    def native_value(self) -> str | None:
        data = self._data
        return data.state if data else None
