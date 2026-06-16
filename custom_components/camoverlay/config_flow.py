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

from .const import CONF_CAMERA, CONF_PREFIX, DEFAULT_PREFIX, DOMAIN


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
            return self.async_create_entry(title="CamOverlay", data=user_input)

        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema(
                {
                    vol.Optional(CONF_PREFIX, default=DEFAULT_PREFIX): str,
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
    """Edit the topic prefix and default camera after setup."""

    async def async_step_init(
        self, user_input: dict[str, Any] | None = None
    ) -> ConfigFlowResult:
        if user_input is not None:
            return self.async_create_entry(data=user_input)

        current = {**self.config_entry.data, **self.config_entry.options}
        schema = {
            vol.Optional(
                CONF_PREFIX, default=current.get(CONF_PREFIX, DEFAULT_PREFIX)
            ): str,
        }
        camera_default = current.get(CONF_CAMERA)
        camera_selector = selector.EntitySelector(
            selector.EntitySelectorConfig(domain="camera")
        )
        if camera_default:
            schema[vol.Optional(CONF_CAMERA, default=camera_default)] = camera_selector
        else:
            schema[vol.Optional(CONF_CAMERA)] = camera_selector

        return self.async_show_form(step_id="init", data_schema=vol.Schema(schema))
