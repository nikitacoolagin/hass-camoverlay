"""Shared entity base for CamOverlay."""
from __future__ import annotations

from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo, Entity

from .const import DOMAIN, SIGNAL_DEVICE_UPDATE
from .coordinator import CamOverlayCoordinator


class CamOverlayEntity(Entity):
    """Base class wiring an entity to one device's push updates."""

    _attr_should_poll = False
    _attr_has_entity_name = True

    def __init__(self, coordinator: CamOverlayCoordinator, device: str) -> None:
        self.coordinator = coordinator
        self.device = device
        self._attr_unique_id = f"{coordinator.prefix}_{device}"
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, f"{coordinator.prefix}_{device}")},
            name=f"CamOverlay {device}",
            manufacturer="CamOverlay",
            model=device,
        )

    @property
    def _data(self):
        return self.coordinator.devices.get(self.device)

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(
            async_dispatcher_connect(
                self.hass,
                SIGNAL_DEVICE_UPDATE.format(self.device),
                self.async_write_ha_state,
            )
        )
