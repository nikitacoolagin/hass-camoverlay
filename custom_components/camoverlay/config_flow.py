"""Config flow for CamOverlay."""
from __future__ import annotations

from typing import Any

import voluptuous as vol

from homeassistant.config_entries import (
    ConfigEntry,
    ConfigFlow,
    ConfigFlowResult,
    OptionsFlow,
)
from homeassistant.core import callback
from homeassistant.helpers import selector

from .const import (
    CONF_CAMERA,
    CONF_CAMERAS,
    CONF_GO2RTC,
    CONF_PREFIX,
    DEFAULT_PREFIX,
    DOMAIN,
)


def _parse_cameras(raw: Any) -> list[str]:
    """Accept a list or a newline/comma separated string of camera source names."""
    if isinstance(raw, list):
        items = raw
    elif isinstance(raw, str):
        items = raw.replace(",", "\n").splitlines()
    else:
        items = []
    return [c.strip() for c in items if c and c.strip()]


def _normalize_input(user_input: dict[str, Any]) -> dict[str, Any]:
    data = dict(user_input)
    if CONF_CAMERAS in data:
        data[CONF_CAMERAS] = _parse_cameras(data[CONF_CAMERAS])
    if CONF_GO2RTC in data and isinstance(data[CONF_GO2RTC], str):
        data[CONF_GO2RTC] = data[CONF_GO2RTC].strip()
    return data


def _multiline() -> selector.TextSelector:
    return selector.TextSelector(selector.TextSelectorConfig(multiline=True))


class CamOverlayConfigFlow(ConfigFlow, domain=DOMAIN):
    """Handle the initial setup."""

    VERSION = 1

    async def async_step_user(
        self, user_input: dict[str, Any] | None = None
    ) -> ConfigFlowResult:
        await self.async_set_unique_id(DOMAIN)
        self._abort_if_unique_id_configured()

        if not self.hass.config_entries.async_entries("mqtt"):
            return self.async_abort(reason="mqtt_required")

        if user_input is not None:
            return self.async_create_entry(
                title="CamOverlay", data=_normalize_input(user_input)
            )

        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema(
                {
                    vol.Optional(CONF_PREFIX, default=DEFAULT_PREFIX): str,
                    vol.Optional(CONF_GO2RTC): str,
                    vol.Optional(CONF_CAMERAS): _multiline(),
                    vol.Optional(CONF_CAMERA): selector.EntitySelector(
                        selector.EntitySelectorConfig(domain="camera")
                    ),
                }
            ),
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry: ConfigEntry) -> OptionsFlow:
        return CamOverlayOptionsFlow()


class CamOverlayOptionsFlow(OptionsFlow):
    """Edit the prefix, go2rtc URL, camera list and default camera after setup."""

    async def async_step_init(
        self, user_input: dict[str, Any] | None = None
    ) -> ConfigFlowResult:
        if user_input is not None:
            return self.async_create_entry(data=_normalize_input(user_input))

        current = {**self.config_entry.data, **self.config_entry.options}

        schema: dict[Any, Any] = {
            vol.Optional(
                CONF_PREFIX, default=current.get(CONF_PREFIX, DEFAULT_PREFIX)
            ): str,
        }

        go2rtc_default = current.get(CONF_GO2RTC)
        if go2rtc_default:
            schema[vol.Optional(CONF_GO2RTC, default=go2rtc_default)] = str
        else:
            schema[vol.Optional(CONF_GO2RTC)] = str

        cameras_default = "\n".join(current.get(CONF_CAMERAS, []))
        schema[
            vol.Optional(CONF_CAMERAS, default=cameras_default)
        ] = _multiline()

        camera_default = current.get(CONF_CAMERA)
        camera_selector = selector.EntitySelector(
            selector.EntitySelectorConfig(domain="camera")
        )
        if camera_default:
            schema[vol.Optional(CONF_CAMERA, default=camera_default)] = camera_selector
        else:
            schema[vol.Optional(CONF_CAMERA)] = camera_selector

        return self.async_show_form(step_id="init", data_schema=vol.Schema(schema))
