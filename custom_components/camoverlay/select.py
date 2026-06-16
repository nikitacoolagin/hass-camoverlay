"""Selects per CamOverlay device: PiP size and corner (used by the Show PiP button)."""
from __future__ import annotations

from homeassistant.components.select import SelectEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.restore_state import RestoreEntity

from .const import (
    CORNER_FROM_LABEL,
    CORNER_LABELS,
    DOMAIN,
    SIGNAL_NEW_DEVICE,
    SIZE_FROM_LABEL,
    SIZE_LABELS,
)
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
                CamOverlaySizeSelect(coordinator, device),
                CamOverlayCornerSelect(coordinator, device),
            ]
        )

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_NEW_DEVICE, _add))
    for device in list(coordinator.devices):
        _add(device)


class CamOverlaySizeSelect(CamOverlayEntity, SelectEntity, RestoreEntity):
    _attr_name = "PiP size"
    _attr_icon = "mdi:resize"
    _attr_options = list(SIZE_LABELS.values())

    def __init__(self, coordinator: CamOverlayCoordinator, device: str) -> None:
        super().__init__(coordinator, device)
        self._attr_unique_id = f"{coordinator.prefix}_{device}_pip_size"

    @property
    def current_option(self) -> str | None:
        data = self._data
        return SIZE_LABELS.get(data.size) if data else None

    async def async_select_option(self, option: str) -> None:
        data = self._data
        if data:
            data.size = SIZE_FROM_LABEL[option]
            self.async_write_ha_state()

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        last = await self.async_get_last_state()
        data = self._data
        if last and data and last.state in SIZE_FROM_LABEL:
            data.size = SIZE_FROM_LABEL[last.state]


class CamOverlayCornerSelect(CamOverlayEntity, SelectEntity, RestoreEntity):
    _attr_name = "PiP corner"
    _attr_icon = "mdi:crop-free"
    _attr_options = list(CORNER_LABELS.values())

    def __init__(self, coordinator: CamOverlayCoordinator, device: str) -> None:
        super().__init__(coordinator, device)
        self._attr_unique_id = f"{coordinator.prefix}_{device}_pip_corner"

    @property
    def current_option(self) -> str | None:
        data = self._data
        return CORNER_LABELS.get(data.corner) if data else None

    async def async_select_option(self, option: str) -> None:
        data = self._data
        if data:
            data.corner = CORNER_FROM_LABEL[option]
            self.async_write_ha_state()

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        last = await self.async_get_last_state()
        data = self._data
        if last and data and last.state in CORNER_FROM_LABEL:
            data.corner = CORNER_FROM_LABEL[last.state]
