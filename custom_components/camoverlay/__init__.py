"""The CamOverlay integration."""
from __future__ import annotations

import voluptuous as vol

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers import config_validation as cv

from .const import (
    ATTR_CAMERAS,
    ATTR_CORNER,
    ATTR_DEVICE,
    ATTR_SIZE,
    CONF_CAMERAS,
    CONF_GO2RTC,
    CONF_PREFIX,
    CORNERS,
    DEFAULT_CORNER,
    DEFAULT_PREFIX,
    DEFAULT_SIZE,
    DOMAIN,
    PLATFORMS,
    SERVICE_FULLSCREEN,
    SERVICE_PIP,
    SERVICE_STOP,
    SIZES,
)
from .coordinator import CamOverlayCoordinator


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Set up CamOverlay from a config entry."""
    conf = {**entry.data, **entry.options}
    prefix = conf.get(CONF_PREFIX, DEFAULT_PREFIX)
    go2rtc_url = conf.get(CONF_GO2RTC)
    cameras = conf.get(CONF_CAMERAS, [])

    coordinator = CamOverlayCoordinator(hass, prefix, go2rtc_url, cameras)
    await coordinator.async_start()

    hass.data.setdefault(DOMAIN, {})[entry.entry_id] = coordinator

    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)

    _async_register_services(hass)
    entry.async_on_unload(entry.add_update_listener(_async_reload))

    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    """Unload a config entry."""
    unload_ok = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unload_ok:
        coordinator: CamOverlayCoordinator = hass.data[DOMAIN].pop(entry.entry_id)
        await coordinator.async_stop()
        if not hass.data[DOMAIN]:
            for service in (SERVICE_FULLSCREEN, SERVICE_PIP, SERVICE_STOP):
                hass.services.async_remove(DOMAIN, service)
    return unload_ok


async def _async_reload(hass: HomeAssistant, entry: ConfigEntry) -> None:
    await hass.config_entries.async_reload(entry.entry_id)


def _resolve_coordinator(hass: HomeAssistant, device: str) -> CamOverlayCoordinator:
    """Pick the coordinator that knows this device (or the only one)."""
    coordinators: list[CamOverlayCoordinator] = list(hass.data[DOMAIN].values())
    for coord in coordinators:
        if device in coord.devices:
            return coord
    if len(coordinators) == 1:
        return coordinators[0]
    raise HomeAssistantError(f"No CamOverlay device named '{device}' is known")


def _async_register_services(hass: HomeAssistant) -> None:
    if hass.services.has_service(DOMAIN, SERVICE_FULLSCREEN):
        return

    async def _fullscreen(call: ServiceCall) -> None:
        device = call.data[ATTR_DEVICE]
        cameras = call.data.get(ATTR_CAMERAS)
        await _resolve_coordinator(hass, device).async_fullscreen(device, cameras)

    async def _stop(call: ServiceCall) -> None:
        device = call.data[ATTR_DEVICE]
        await _resolve_coordinator(hass, device).async_stop_cmd(device)

    async def _pip(call: ServiceCall) -> None:
        device = call.data[ATTR_DEVICE]
        size = call.data.get(ATTR_SIZE, DEFAULT_SIZE)
        corner = call.data.get(ATTR_CORNER, DEFAULT_CORNER)
        cameras = call.data.get(ATTR_CAMERAS)
        await _resolve_coordinator(hass, device).async_pip(device, size, corner, cameras)

    hass.services.async_register(
        DOMAIN,
        SERVICE_FULLSCREEN,
        _fullscreen,
        schema=vol.Schema(
            {
                vol.Required(ATTR_DEVICE): cv.string,
                vol.Optional(ATTR_CAMERAS): vol.All(cv.ensure_list, [cv.string]),
            }
        ),
    )
    hass.services.async_register(
        DOMAIN,
        SERVICE_STOP,
        _stop,
        schema=vol.Schema({vol.Required(ATTR_DEVICE): cv.string}),
    )
    hass.services.async_register(
        DOMAIN,
        SERVICE_PIP,
        _pip,
        schema=vol.Schema(
            {
                vol.Required(ATTR_DEVICE): cv.string,
                vol.Optional(ATTR_SIZE, default=DEFAULT_SIZE): vol.In(SIZES),
                vol.Optional(ATTR_CORNER, default=DEFAULT_CORNER): vol.In(CORNERS),
                vol.Optional(ATTR_CAMERAS): vol.All(cv.ensure_list, [cv.string]),
            }
        ),
    )
