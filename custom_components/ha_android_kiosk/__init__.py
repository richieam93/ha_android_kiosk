from __future__ import annotations

import asyncio
import logging
import mimetypes
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import voluptuous as vol

from homeassistant.components import frontend, panel_custom
from homeassistant.components.http import HomeAssistantView, StaticPathConfig
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import Platform
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers import device_registry as dr
from homeassistant.helpers import entity_registry as er
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.storage import Store

from .const import (
    DOMAIN,
    EVENT_COMMAND,
    EVENT_STATUS,
    PANEL_URL,
    SIGNAL_DEVICE_REGISTERED,
    SIGNAL_DEVICE_STATE_UPDATED,
    STATIC_URL,
    STORAGE_KEY,
    STORAGE_VERSION,
)

_LOGGER = logging.getLogger(__name__)

PLATFORMS: list[Platform] = [
    Platform.SENSOR,
    Platform.BINARY_SENSOR,
    Platform.SWITCH,
    Platform.NUMBER,
    Platform.MEDIA_PLAYER,
]

DEFAULT_CONFIG: dict[str, Any] = {"devices": {}}

COMMAND_PERMISSIONS: dict[str, str] = {
    # Browser / dashboard / rotation
    "browser_config": "browser",
    "set_browser_config": "browser",
    "show_page": "display",
    "set_page": "display",
    "goto_screen": "display",
    "next_screen": "display",
    "previous_screen": "display",
    "reload_page": "display",
    "sync_settings": "display",
    "fetch_config": "display",
    "recover_dashboard": "display",
    "reapply_visuals": "display",
    "set_pages": "rotation",
    "rotation": "rotation",
    "rotation_start": "rotation",
    "rotation_stop": "rotation",
    "pause_rotation": "rotation",
    "resume_rotation": "rotation",
    # Overlays
    "ticker": "ticker",
    "clear_ticker": "ticker",
    "ticker_config": "ticker",
    "banner": "alerts",
    "toast": "alerts",
    "alert": "alerts",
    "clear_alert": "alerts",
    "clear": "alerts",
    "clear_banner": "alerts",
    "clear_weather": "weather",
    "clear_camera": "camera_overlay",
    "clock": "clock",
    "weather": "weather",
    "camera": "camera_overlay",
    "status_overlay": "alerts",
    "overlay_sequence": "alerts",
    # Device controls
    "screen": "display",
    "set_screen_power": "display",
    "fullscreen": "display",
    "brightness": "brightness",
    "volume": "volume",
    "orientation": "orientation",
    "vibrate": "vibrate",
    "camera_front": "camera_front",
    "camera_control": "camera",
    "camera_all_off": "camera",
    "camera_motion_detection": "camera_motion_detection",
    "motion_detection": "motion_detection",
    "set_device_setting": "display",
    "open_android_settings": "display",
    "restart_app": "display",
    # Media
    "play_media": "media_player",
    "play_sound": "media_player",
    "play_announcement": "media_player",
    "media_play": "media_player",
    "media_pause": "media_player",
    "media_stop": "media_player",
    "media_next": "media_player",
    "media_previous": "media_player",
    "media_mute": "media_player",
    "media_unmute": "media_player",
    "media_seek": "media_player",
    "media_loop": "media_player",
    "set_media_volume": "media_player",
    "audio_config": "media_player",
    "set_audio_config": "media_player",
    "tts_speak": "media_player",
    "tts_stop": "media_player",
    "stop_media": "media_player",
    "stop_audio": "media_player",
    "delete_device": None,
    "cleanup_devices": None,
}

PERMISSION_FALLBACKS: dict[str, set[str]] = {
    "browser": {"display"},
    "rotation": {"display"},
    "ticker": {"display"},
    "alerts": {"display"},
    "clock": {"display"},
    "weather": {"display"},
    "camera_overlay": {"display", "camera"},
    "camera_front": {"camera"},
    "camera_motion_detection": {"camera"},
}

GENERIC_SERVICE_SCHEMA = vol.Schema(
    {
        vol.Optional("device"): vol.Any(cv.string, [cv.string]),
        vol.Optional("device_id"): cv.string,
        vol.Optional("command"): cv.string,
        vol.Optional("payload", default={}): dict,
    },
    extra=vol.ALLOW_EXTRA,
)

# Public service names. Most are friendly wrappers around the Android command bus.
SERVICE_NAMES: tuple[str, ...] = (
    "set_browser_config",
    "set_dashboard_language",
    "open_url",
    "set_pages",
    "send_ticker_message",
    "clear_ticker",
    "update_ticker_config",
    "show_toast",
    "show_banner",
    "show_alert",
    "show_clock",
    "show_weather",
    "show_camera",
    "show_sensor_overlay",
    "show_status_overlay",
    "show_overlay_sequence",
    "clear_banner",
    "clear_weather",
    "clear_camera",
    "clear_alert",
    "next_screen",
    "previous_screen",
    "goto_screen",
    "pause_rotation",
    "resume_rotation",
    "reload_page",
    "clear_webview_cache",
    "clear_overlays",
    "sync_settings",
    "recover_dashboard",
    "identify_device",
    "set_screen_power",
    "set_brightness",
    "set_volume",
    "set_screen_orientation",
    "set_device_setting",
    "restart_app",
    "open_android_settings",
    "vibrate_device",
    "report_device_state",
    "play_sound",
    "play_announcement",
    "tts_speak",
    "stop_audio",
    "play_media",
    "stop_media",
    "media_pause",
    "media_resume",
    "media_next",
    "media_previous",
    "media_seek",
    "media_mute",
    "media_unmute",
    "media_loop",
    "set_media_volume",
    "set_audio_config",
    "tts_stop",
    "delete_device",
    "cleanup_devices",
    "set_background_slideshow",
    "set_background_album",
    "set_background_image",
    "clear_background",
    # Backwards-compatible/raw services from v1/v2.
    "send_command",
    "set_page",
    "show_message",
    "apply_device_profile",
)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


async def async_setup(hass: HomeAssistant, config: dict[str, Any]) -> bool:
    return await _async_setup_once(hass)


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    await _async_setup_once(hass)
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    unload_ok = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if not unload_ok:
        return False
    for service in SERVICE_NAMES:
        hass.services.async_remove(DOMAIN, service)
    frontend.async_remove_panel(hass, PANEL_URL, warn_if_unknown=False)
    hass.data.pop(DOMAIN, None)
    return True


async def _async_setup_once(hass: HomeAssistant) -> bool:
    data = hass.data.setdefault(DOMAIN, {})
    if data.get("setup_done"):
        return True

    data["store"] = Store(hass, STORAGE_VERSION, STORAGE_KEY)
    data["device_states"] = {}
    data["config_cache"] = await _async_load_config(hass)

    static_path = Path(__file__).parent / "static"
    await hass.http.async_register_static_paths([StaticPathConfig(STATIC_URL, str(static_path), False)])

    hass.http.register_view(KioskConfigView())
    hass.http.register_view(KioskDevicesView())
    hass.http.register_view(KioskRegisterView())
    hass.http.register_view(KioskStatusView())
    hass.http.register_view(KioskCommandView())
    hass.http.register_view(KioskDeviceConfigView())
    hass.http.register_view(KioskDeviceDeleteView())
    hass.http.register_view(KioskCleanupView())
    hass.http.register_view(KioskBackgroundsView())
    hass.http.register_view(KioskBackgroundAlbumsView())
    hass.http.register_view(KioskBackgroundAlbumView())
    hass.http.register_view(KioskBackgroundUploadView())
    hass.http.register_view(KioskBackgroundDeleteView())
    hass.http.register_view(KioskBackgroundAlbumFileDeleteView())

    await panel_custom.async_register_panel(
        hass,
        frontend_url_path=PANEL_URL,
        webcomponent_name="ha-android-kiosk-panel",
        sidebar_title="Android Kiosk",
        sidebar_icon="mdi:tablet-dashboard",
        module_url=f"{STATIC_URL}/ha-android-kiosk-panel.js?v=1.9.18",
        require_admin=True,
        config_panel_domain=DOMAIN,
    )

    async def dispatch_service(call: ServiceCall) -> None:
        await _handle_named_service(hass, call)

    for service in SERVICE_NAMES:
        hass.services.async_register(DOMAIN, service, dispatch_service, schema=GENERIC_SERVICE_SCHEMA)

    data["setup_done"] = True
    return True


async def _async_load_config(hass: HomeAssistant) -> dict[str, Any]:
    store: Store = hass.data[DOMAIN]["store"]
    stored = await store.async_load()
    if not isinstance(stored, dict):
        stored = dict(DEFAULT_CONFIG)
    stored.setdefault("devices", {})
    return stored


async def _async_save_config(hass: HomeAssistant, config: dict[str, Any]) -> dict[str, Any]:
    config.setdefault("devices", {})
    store: Store = hass.data[DOMAIN]["store"]
    await store.async_save(config)
    hass.data[DOMAIN]["config_cache"] = config
    return config


def _signal_device_state_updated(hass: HomeAssistant, device_id: str) -> None:
    """Notify entities about a changed device state on the HA event loop."""
    try:
        hass.loop.call_soon_threadsafe(async_dispatcher_send, hass, SIGNAL_DEVICE_STATE_UPDATED, device_id)
    except RuntimeError:
        async_dispatcher_send(hass, SIGNAL_DEVICE_STATE_UPDATED, device_id)


def _signal_device_registered(hass: HomeAssistant, device_id: str) -> None:
    """Notify platforms about a new device on the HA event loop."""
    try:
        hass.loop.call_soon_threadsafe(async_dispatcher_send, hass, SIGNAL_DEVICE_REGISTERED, device_id)
    except RuntimeError:
        async_dispatcher_send(hass, SIGNAL_DEVICE_REGISTERED, device_id)


def _merge_device_state(hass: HomeAssistant, device_id: str, data: dict[str, Any]) -> None:
    state = hass.data[DOMAIN].setdefault("device_states", {}).setdefault(device_id, {})
    state.update(data)
    state["last_seen"] = _now()
    if "status" in data:
        state["last_status"] = data.get("status")
    if "command" in data:
        state["last_command"] = data.get("command")
    _signal_device_state_updated(hass, device_id)


def _permission_for_command(command: str) -> str | None:
    return COMMAND_PERMISSIONS.get(command)


def _allowed_by_config(allowed_functions: Any, permission: str | None) -> bool:
    if permission is None:
        return True
    if not isinstance(allowed_functions, list) or not allowed_functions:
        # Backwards-compatible: older devices without a permission list allow all.
        return True
    allowed = {str(item) for item in allowed_functions}
    if permission in allowed:
        return True
    return bool(PERMISSION_FALLBACKS.get(permission, set()) & allowed)


async def _can_fire_command(hass: HomeAssistant, device_id: str, command: str) -> bool:
    config = await _async_load_config(hass)
    device = config.get("devices", {}).get(device_id, {})
    return _allowed_by_config(device.get("allowed_functions"), _permission_for_command(command))


async def _fire_command(hass: HomeAssistant, device_id: str, command: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    if not await _can_fire_command(hass, device_id, command):
        permission = _permission_for_command(command) or command
        raise vol.Invalid(f"Function '{permission}' is disabled for Android kiosk device '{device_id}'")
    data = {
        "device_id": device_id,
        "command": command,
        "payload": payload or {},
        "sent_at": _now(),
    }
    hass.bus.async_fire(EVENT_COMMAND, data)
    state = hass.data[DOMAIN].setdefault("device_states", {}).setdefault(device_id, {})
    state["last_command"] = command
    state["last_command_payload"] = payload or {}
    state["last_command_at"] = data["sent_at"]
    _signal_device_state_updated(hass, device_id)
    return data


async def _fire_command_if_allowed(hass: HomeAssistant, device_id: str, command: str, payload: dict[str, Any] | None = None) -> None:
    if await _can_fire_command(hass, device_id, command):
        await _fire_command(hass, device_id, command, payload)


async def _async_delete_device(hass: HomeAssistant, device_id: str, remove_registry: bool = True) -> dict[str, Any]:
    """Remove a kiosk device from storage and optionally HA registries."""
    device_id = str(device_id or "").strip()
    if not device_id:
        raise vol.Invalid("device_id is required")

    config = await _async_load_config(hass)
    devices = config.setdefault("devices", {})
    existed = devices.pop(device_id, None) is not None
    hass.data.get(DOMAIN, {}).setdefault("device_states", {}).pop(device_id, None)
    await _async_save_config(hass, config)

    removed_entities: list[str] = []
    removed_device = False

    if remove_registry:
        entity_registry = er.async_get(hass)
        device_registry = dr.async_get(hass)

        # Remove entity registry entries belonging to this integration/device.
        try:
            device_entry = device_registry.async_get_device({(DOMAIN, device_id)})
        except Exception:
            device_entry = None

        if device_entry is not None:
            try:
                for entry in list(er.async_entries_for_device(entity_registry, device_entry.id, include_disabled_entities=True)):
                    if getattr(entry, "platform", None) == DOMAIN:
                        entity_registry.async_remove(entry.entity_id)
                        removed_entities.append(entry.entity_id)
            except Exception as err:
                _LOGGER.warning("Could not remove all entity registry entries for %s: %s", device_id, err)

        # Fallback for orphaned entity entries where the device registry link is missing.
        try:
            entries = list(getattr(entity_registry, "entities", {}).values())
            for entry in entries:
                unique_id = str(getattr(entry, "unique_id", "") or "")
                if getattr(entry, "platform", None) == DOMAIN and (unique_id == device_id or unique_id.startswith(f"{device_id}_")):
                    if entry.entity_id not in removed_entities:
                        entity_registry.async_remove(entry.entity_id)
                        removed_entities.append(entry.entity_id)
        except Exception as err:
            _LOGGER.warning("Could not remove orphan entity registry entries for %s: %s", device_id, err)

        if device_entry is not None:
            try:
                device_registry.async_remove_device(device_entry.id)
                removed_device = True
            except Exception as err:
                _LOGGER.warning("Could not remove device registry entry for %s: %s", device_id, err)

    _signal_device_state_updated(hass, device_id)
    return {
        "ok": True,
        "device_id": device_id,
        "existed": existed,
        "removed_entities": removed_entities,
        "removed_entity_count": len(removed_entities),
        "removed_device_registry_entry": removed_device,
    }


def _as_list(value: Any) -> list[Any]:
    if isinstance(value, list):
        return value
    if isinstance(value, tuple):
        return list(value)
    return [value]


def _clean_payload(data: dict[str, Any]) -> dict[str, Any]:
    payload = dict(data.get("payload") or {}) if isinstance(data.get("payload"), dict) else {}
    for key, value in data.items():
        if key not in {"device", "device_id", "payload"}:
            payload[key] = value
    # Friendly aliases used by the service file.
    if "message" in payload and "text" not in payload:
        payload["text"] = payload["message"]
    if "brightness" in payload and "value" not in payload:
        payload["value"] = payload["brightness"]
    if "volume" in payload and "value" not in payload:
        payload["value"] = payload["volume"]
    if "media_url" in payload and "url" not in payload:
        payload["url"] = payload["media_url"]
    if "sound_url" in payload and "url" not in payload:
        payload["url"] = payload["sound_url"]
    if "screen_id" in payload and "page_id" not in payload:
        payload["page_id"] = payload["screen_id"]
    return payload


async def _resolve_devices(hass: HomeAssistant, target: Any) -> list[str]:
    config = await _async_load_config(hass)
    devices = config.get("devices", {})
    states = hass.data.get(DOMAIN, {}).get("device_states", {})
    known_ids = list(dict.fromkeys([*devices.keys(), *states.keys()]))
    if target in (None, "", "all", "*"):
        return known_ids

    resolved: list[str] = []
    for item in _as_list(target):
        item_str = str(item).strip()
        if not item_str:
            continue
        matched = False
        if item_str.lower() in {"all", "*"}:
            for device_id in known_ids:
                if device_id not in resolved:
                    resolved.append(device_id)
            continue
        if item_str in devices or item_str in states:
            if item_str not in resolved:
                resolved.append(item_str)
            matched = True
            continue
        item_norm = item_str.casefold()
        for device_id, cfg in devices.items():
            if str(cfg.get("name", "")).casefold() == item_norm and device_id not in resolved:
                resolved.append(device_id)
                matched = True
        # Allow addressing a not-yet-stored device id explicitly.
        if not matched and item_str not in resolved:
            resolved.append(item_str)
    return resolved



def _format_state_for_overlay(hass: HomeAssistant, item: Any) -> str:
    """Format one Home Assistant entity/template descriptor for a display overlay."""
    if isinstance(item, str):
        entity_id = item
        label = ""
        icon = ""
    elif isinstance(item, dict):
        entity_id = str(item.get("entity_id") or item.get("entity") or "").strip()
        label = str(item.get("label") or item.get("name") or "").strip()
        icon = str(item.get("icon") or "").strip()
    else:
        return ""
    if not entity_id:
        return ""
    state = hass.states.get(entity_id)
    if state is None:
        return f"{icon + ' ' if icon else ''}{label or entity_id}: -"
    unit = ""
    if isinstance(item, dict):
        unit = str(item.get("unit") or item.get("unit_of_measurement") or "").strip()
    if not unit:
        unit = str(state.attributes.get("unit_of_measurement") or "").strip()
    shown_label = label or str(state.attributes.get("friendly_name") or entity_id)
    value = str(state.state)
    if value in {"unknown", "unavailable", "None"}:
        value = "-"
    prefix = f"{icon} " if icon else ""
    suffix = f" {unit}" if unit and unit not in value else ""
    return f"{prefix}{shown_label}: {value}{suffix}"


def _items_from_payload(payload: dict[str, Any]) -> list[Any]:
    items = payload.get("items") or payload.get("entities") or payload.get("sensors") or []
    if isinstance(items, str):
        return [part.strip() for part in re.split(r"[,\n]", items) if part.strip()]
    if isinstance(items, list):
        return items
    return []


def _sensor_overlay_payload(hass: HomeAssistant, payload: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    overlay = str(payload.get("overlay") or payload.get("type") or "ticker").strip().lower()
    parts = [text for text in (_format_state_for_overlay(hass, item) for item in _items_from_payload(payload)) if text]
    separator = str(payload.get("separator") or "   •   ")
    message = str(payload.get("message") or payload.get("text") or separator.join(parts)).strip()
    out = dict(payload)
    out.pop("items", None)
    out.pop("entities", None)
    out.pop("sensors", None)
    out["text"] = message
    out.setdefault("title", payload.get("title") or "Status")
    out.setdefault("wake_screen", payload.get("wake_screen", True))
    command = {
        "ticker": "ticker",
        "banner": "banner",
        "alert": "alert",
        "toast": "toast",
        "weather": "weather",
    }.get(overlay, "ticker")
    return command, out


def _augment_weather_payload_from_entity(hass: HomeAssistant, payload: dict[str, Any]) -> dict[str, Any]:
    if payload.get("text") or payload.get("message"):
        return payload
    entity_id = str(payload.get("entity_id") or payload.get("weather_entity") or "").strip()
    if not entity_id:
        return payload
    state = hass.states.get(entity_id)
    if state is None:
        return payload
    attrs = state.attributes
    temp = attrs.get("temperature")
    temp_unit = attrs.get("temperature_unit") or hass.config.units.temperature_unit
    humidity = attrs.get("humidity")
    wind = attrs.get("wind_speed")
    wind_unit = attrs.get("wind_speed_unit") or ""
    condition = state.state
    lines: list[str] = []
    if condition and condition not in {"unknown", "unavailable"}:
        lines.append(str(condition).replace("_", " ").title())
    if temp is not None:
        lines.append(f"{temp} {temp_unit}")
    if humidity is not None:
        lines.append(f"Feuchte {humidity}%")
    if wind is not None:
        lines.append(f"Wind {wind} {wind_unit}".strip())
    out = dict(payload)
    out["text"] = "\n".join(lines) if lines else entity_id
    out.setdefault("title", attrs.get("friendly_name") or "Wetter")
    return out


async def _run_overlay_sequence(hass: HomeAssistant, device_ids: list[str], steps: list[Any]) -> None:
    """Run a small command sequence. Intended for short copy/paste automations."""
    for raw_step in steps:
        if not isinstance(raw_step, dict):
            continue
        wait = raw_step.get("wait") or raw_step.get("delay_before")
        if wait:
            await asyncio.sleep(max(0, min(float(wait), 300)))
        service_name = str(raw_step.get("service") or raw_step.get("command") or raw_step.get("type") or "").strip()
        if not service_name:
            continue
        if service_name in {"wait", "delay", "sleep"}:
            await asyncio.sleep(max(0, min(float(raw_step.get("seconds", raw_step.get("duration", 1))), 300)))
            continue
        step_payload = {k: v for k, v in raw_step.items() if k not in {"service", "command", "type", "wait", "delay_before", "delay_after"}}
        if service_name == "show_sensor_overlay":
            command, step_payload = _sensor_overlay_payload(hass, step_payload)
        else:
            command, step_payload = _service_command(service_name, _clean_payload(step_payload))
        for device_id in device_ids:
            await _fire_command(hass, device_id, command, step_payload)
        delay_after = raw_step.get("delay_after")
        if delay_after:
            await asyncio.sleep(max(0, min(float(delay_after), 300)))

def _service_command(service: str, payload: dict[str, Any]) -> tuple[str, dict[str, Any]]:
    if service == "send_command":
        command = str(payload.pop("command", "")).strip()
        inner = payload.pop("payload", None)
        if isinstance(inner, dict):
            payload.update(inner)
        if not command:
            raise vol.Invalid("command is required")
        return command, payload
    if service == "set_page":
        payload.setdefault("page_id", payload.get("path") or payload.get("url") or payload.get("page"))
        return "goto_screen", payload
    if service == "show_message":
        return str(payload.pop("message_type", "toast") or "toast"), payload
    if service == "apply_device_profile":
        return "apply_profile", payload
    if service == "clear_overlays":
        payload.setdefault("target", "overlays")
    if service == "clear_banner":
        payload.setdefault("target", "banner")
    if service == "clear_weather":
        payload.setdefault("target", "weather")
    if service == "clear_camera":
        payload.setdefault("target", "camera")

    mapping = {
        "set_browser_config": "browser_config",
        "set_dashboard_language": "browser_config",
        "open_url": "show_page",
        "set_pages": "set_pages",
        "send_ticker_message": "ticker",
        "clear_ticker": "clear_ticker",
        "update_ticker_config": "ticker_config",
        "show_toast": "toast",
        "show_banner": "banner",
        "show_alert": "alert",
        "show_clock": "clock",
        "show_weather": "weather",
        "show_camera": "camera",
        "show_status_overlay": "status_overlay",
        "show_overlay_sequence": "overlay_sequence",
        "clear_alert": "clear_alert",
        "next_screen": "next_screen",
        "previous_screen": "previous_screen",
        "goto_screen": "goto_screen",
        "pause_rotation": "pause_rotation",
        "resume_rotation": "resume_rotation",
        "reload_page": "reload_page",
        "clear_webview_cache": "clear_webview_cache",
        "clear_overlays": "clear",
        "sync_settings": "sync_settings",
        "recover_dashboard": "recover_dashboard",
        "identify_device": "identify_device",
        "set_screen_power": "set_screen_power",
        "set_brightness": "brightness",
        "set_volume": "volume",
        "set_screen_orientation": "orientation",
        "set_device_setting": "set_device_setting",
        "restart_app": "restart_app",
        "open_android_settings": "open_android_settings",
        "vibrate_device": "vibrate",
        "report_device_state": "report_device_state",
        "play_sound": "play_sound",
        "play_announcement": "play_announcement",
        "tts_speak": "tts_speak",
        "stop_audio": "stop_media",
        "play_media": "play_media",
        "stop_media": "stop_media",
        "media_pause": "media_pause",
        "media_resume": "media_play",
        "media_next": "media_next",
        "media_previous": "media_previous",
        "media_seek": "media_seek",
        "media_mute": "media_mute",
        "media_unmute": "media_unmute",
        "media_loop": "media_loop",
        "set_media_volume": "set_media_volume",
        "set_audio_config": "audio_config",
        "tts_stop": "tts_stop",
        "delete_device": "delete_device",
        "cleanup_devices": "cleanup_devices",
        "set_background_slideshow": "background_slideshow",
        "set_background_album": "background_slideshow",
        "set_background_image": "set_background",
        "clear_background": "clear_background",
    }
    return mapping.get(service, service), payload


def _optimistic_state(hass: HomeAssistant, device_id: str, service: str, payload: dict[str, Any]) -> None:
    state = hass.data[DOMAIN].setdefault("device_states", {}).setdefault(device_id, {})
    if service == "set_brightness" and "value" in payload:
        state["brightness"] = payload["value"]
    elif service == "set_volume" and "value" in payload:
        state["volume"] = payload["value"]
    elif service == "set_screen_power":
        state["screen_blank"] = not bool(payload.get("power", payload.get("value", True)))
    elif service == "clear_alert":
        state["last_status"] = "alerts_cleared"
    elif service == "restart_app":
        state["last_status"] = "restart_requested"
    elif service == "play_media":
        state["media_state"] = "buffering"
    elif service in {"stop_media", "stop_audio"}:
        state["media_state"] = "idle"
    elif service == "media_pause":
        state["media_state"] = "paused"
    elif service == "media_resume":
        state["media_state"] = "playing"
    elif service == "media_mute":
        state["media_muted"] = True
    elif service == "media_unmute":
        state["media_muted"] = False
    elif service == "set_media_volume" and "volume" in payload:
        state["volume"] = payload.get("volume")
    elif service == "set_audio_config":
        state["preferred_audio_stream"] = payload.get("audio_stream", state.get("preferred_audio_stream"))
        state["tts_volume_percent"] = payload.get("tts_volume", state.get("tts_volume_percent"))
        state["media_volume_percent"] = payload.get("media_volume", state.get("media_volume_percent"))
    elif service in {"show_status_overlay", "show_overlay_sequence", "show_sensor_overlay"}:
        state["last_status"] = "overlay_requested"
    elif service == "set_dashboard_language":
        lang = payload.get("dashboard_language", payload.get("language"))
        if lang:
            state["dashboard_language"] = lang
            state["last_status"] = "language_requested"
    elif service == "sync_settings":
        state["last_status"] = "sync_requested"
    elif service == "recover_dashboard":
        state["last_status"] = "recover_requested"
    elif service == "media_seek" and "position" in payload:
        state["media_position"] = payload.get("position")
    elif service in {"set_background_slideshow", "set_background_image"}:
        state["background_status"] = "configured"
    elif service == "clear_background":
        state["background_status"] = "off"
    _signal_device_state_updated(hass, device_id)


async def _handle_named_service(hass: HomeAssistant, call: ServiceCall) -> None:
    service = call.service
    raw = dict(call.data)
    target = raw.get("device", raw.get("device_id", "all"))
    targets = await _resolve_devices(hass, target)
    if not targets:
        raise vol.Invalid("No Android kiosk devices matched the service target")

    payload = _clean_payload(raw)

    if service == "apply_device_profile":
        for device_id in targets:
            await _apply_profile(hass, device_id)
        return

    if service == "delete_device":
        remove_registry = bool(payload.get("remove_registry", True))
        for device_id in targets:
            await _async_delete_device(hass, device_id, remove_registry=remove_registry)
        return

    if service == "cleanup_devices":
        remove_registry = bool(payload.get("remove_registry", True))
        offline_only = bool(payload.get("offline_only", False))
        for device_id in targets:
            if offline_only:
                state = hass.data.get(DOMAIN, {}).get("device_states", {}).get(device_id, {})
                cfg = (await _async_load_config(hass)).get("devices", {}).get(device_id, {})
                last_seen = state.get("last_seen") or cfg.get("last_seen")
                try:
                    parsed = datetime.fromisoformat(str(last_seen).replace("Z", "+00:00")) if last_seen else None
                    if parsed and (datetime.now(timezone.utc) - parsed).total_seconds() < 180:
                        continue
                except Exception:
                    pass
            await _async_delete_device(hass, device_id, remove_registry=remove_registry)
        return

    if service == "set_background_album":
        album = _safe_album_name(str(payload.get("album") or "default"))
        albums = _list_background_albums(hass)
        files = next((a.get("files", []) for a in albums if a.get("id") == album), [])
        payload = {
            "enabled": payload.get("enabled", True),
            "album": album,
            "images": [file.get("url") for file in files if file.get("url")],
            "interval": payload.get("interval", 20),
            "random": payload.get("random", False),
            "size": payload.get("size", "cover"),
            "position": payload.get("position", "center"),
            "repeat": payload.get("repeat", "no-repeat"),
            "opacity": payload.get("opacity", 100),
        }

    if service == "show_weather":
        payload = _enrich_weather_payload(hass, payload)
    elif service == "show_sensor_overlay":
        command, payload = _sensor_overlay_payload(hass, payload)
        for device_id in targets:
            _optimistic_state(hass, device_id, service, payload)
            await _fire_command(hass, device_id, command, payload)
        return
    elif service == "show_status_overlay":
        payload = _build_status_overlay_payload(hass, payload)
    elif service == "show_overlay_sequence":
        payload = _build_overlay_sequence_payload(hass, payload)

    command, payload = _service_command(service, payload)
    if command == "apply_profile":
        for device_id in targets:
            await _apply_profile(hass, device_id)
        return

    for device_id in targets:
        _optimistic_state(hass, device_id, service, payload)
        await _fire_command(hass, device_id, command, payload)



def _entity_summary(hass: HomeAssistant, entity_id: str) -> str:
    entity_id = str(entity_id or "").strip()
    if not entity_id:
        return ""
    state = hass.states.get(entity_id)
    if state is None:
        return entity_id
    name = state.attributes.get("friendly_name") or entity_id
    value = state.state
    unit = state.attributes.get("unit_of_measurement") or ""
    if unit and not str(value).endswith(str(unit)):
        value = f"{value} {unit}"
    return f"{name}: {value}"


def _entity_name(hass: HomeAssistant, entity_id: str, fallback: str = "") -> str:
    state = hass.states.get(str(entity_id or "").strip())
    if state is None:
        return fallback or str(entity_id or "")
    return str(state.attributes.get("friendly_name") or fallback or entity_id)


def _enrich_weather_payload(hass: HomeAssistant, payload: dict[str, Any]) -> dict[str, Any]:
    payload = dict(payload)
    entity_id = str(payload.get("entity_id") or "").strip()
    if not entity_id or payload.get("text") or payload.get("message"):
        return payload
    state = hass.states.get(entity_id)
    if state is None:
        return payload
    attrs = state.attributes
    title = payload.get("title") or attrs.get("friendly_name") or "Wetter"
    parts: list[str] = []
    condition = str(state.state or "")
    if condition and condition not in {"unknown", "unavailable"}:
        parts.append(condition)
    temp = attrs.get("temperature")
    unit = attrs.get("temperature_unit") or "°C"
    if temp is not None:
        parts.append(f"{temp}{unit}")
    humidity = attrs.get("humidity")
    if humidity is not None:
        parts.append(f"Feuchte {humidity}%")
    wind = attrs.get("wind_speed")
    wind_unit = attrs.get("wind_speed_unit") or ""
    if wind is not None:
        parts.append(f"Wind {wind}{wind_unit}")
    pressure = attrs.get("pressure")
    pressure_unit = attrs.get("pressure_unit") or "hPa"
    if pressure is not None and payload.get("show_pressure"):
        parts.append(f"Druck {pressure}{pressure_unit}")
    forecast = attrs.get("forecast")
    if isinstance(forecast, list) and forecast and payload.get("show_forecast"):
        first = forecast[0] if isinstance(forecast[0], dict) else {}
        f_temp = first.get("temperature")
        f_cond = first.get("condition")
        if f_temp is not None or f_cond:
            parts.append(f"Vorhersage {f_cond or ''} {f_temp if f_temp is not None else ''}{unit}".strip())
    payload["title"] = title
    payload["text"] = " · ".join(parts) if parts else _entity_summary(hass, entity_id)
    payload.setdefault("icon", "🌦️")
    return payload


def _build_status_overlay_payload(hass: HomeAssistant, payload: dict[str, Any]) -> dict[str, Any]:
    payload = dict(payload)
    rows = payload.get("rows")
    if not isinstance(rows, list):
        rows = []
    entities = payload.get("entities") or payload.get("entity_ids") or []
    if isinstance(entities, str):
        entities = [line.strip() for line in re.split(r"[,\n]", entities) if line.strip()]
    if isinstance(entities, list):
        for entity_id in entities:
            if isinstance(entity_id, dict):
                eid = str(entity_id.get("entity") or entity_id.get("entity_id") or "").strip()
                label = str(entity_id.get("name") or _entity_name(hass, eid, eid)).strip()
                summary = _entity_summary(hass, eid)
                value = summary.split(": ", 1)[1] if ": " in summary else summary
                rows.append({"label": label, "value": value, "icon": entity_id.get("icon", "")})
            else:
                summary = _entity_summary(hass, str(entity_id))
                if summary:
                    if ": " in summary:
                        label, value = summary.split(": ", 1)
                        rows.append({"label": label, "value": value})
                    else:
                        rows.append({"label": str(entity_id), "value": summary})
    payload["rows"] = rows
    if not payload.get("message") and not payload.get("text"):
        lines = []
        for row in rows:
            if isinstance(row, dict):
                icon = f"{row.get('icon')} " if row.get("icon") else ""
                lines.append(f"{icon}{row.get('label', '')}: {row.get('value', '')}".strip())
            else:
                lines.append(str(row))
        payload["message"] = "\n".join(lines)
    payload.setdefault("title", "Status")
    payload.setdefault("mode", "panel")
    payload.setdefault("position", "center")
    return payload


def _build_overlay_sequence_payload(hass: HomeAssistant, payload: dict[str, Any]) -> dict[str, Any]:
    payload = dict(payload)
    steps = payload.get("steps") or payload.get("sequence") or []
    if not isinstance(steps, list):
        steps = []
    normalized = []
    default_duration = int(payload.get("default_duration", payload.get("duration", 8)) or 8)
    for step in steps:
        if not isinstance(step, dict):
            step = {"type": "banner", "message": str(step)}
        step = dict(step)
        step.setdefault("duration", default_duration)
        if step.get("type") == "weather":
            step = _enrich_weather_payload(hass, step)
        elif step.get("type") in {"status", "status_overlay"}:
            step = _build_status_overlay_payload(hass, step)
            step["type"] = "status_overlay"
        normalized.append(step)
    payload["steps"] = normalized
    payload.setdefault("repeat", 1)
    payload.setdefault("gap", 1)
    return payload


async def _apply_profile(hass: HomeAssistant, device_id: str) -> None:
    config = await _async_load_config(hass)
    device = config.get("devices", {}).get(device_id)
    if not device:
        raise vol.Invalid(f"Unknown Android kiosk device: {device_id}")

    background = device.get("background") or {}
    if background:
        await _fire_command_if_allowed(
            hass,
            device_id,
            "background_slideshow",
            {
                "enabled": background.get("enabled", False),
                "album": background.get("album", "default"),
                "images": background.get("images") or [],
                "interval": background.get("interval", 20),
                "random": background.get("random", False),
                "size": background.get("size", "cover"),
                "position": background.get("position", "center"),
                "repeat": background.get("repeat", "no-repeat"),
                "opacity": background.get("opacity", 100),
            },
        )

    browser = device.get("browser") or {}
    if browser:
        browser_payload = {
            "home_url": browser.get("home_url") or browser.get("start_url"),
            "start_url": browser.get("start_url") or browser.get("home_url"),
            "url": browser.get("start_url") or browser.get("home_url"),
            "user_agent": browser.get("user_agent", ""),
            "fullscreen": browser.get("fullscreen", True),
            "keep_screen_on": browser.get("keep_screen_on", True),
            "reload_seconds": browser.get("reload_seconds", 0),
            "webview_debug": browser.get("webview_debug", False),
            "hide_header": browser.get("hide_header", browser.get("hide_ha_header", False)),
            "wait_for_background": browser.get("wait_for_background", True),
            "transparent_webview": browser.get("transparent_webview", True),
            "force_mobile_viewport": browser.get("force_mobile_viewport", False),
            "reveal_ms": browser.get("reveal_ms", 250),
            "zoom_percent": browser.get("zoom_percent", 100),
            "dashboard_language": browser.get("dashboard_language", browser.get("language", "de")),
            "language": browser.get("dashboard_language", browser.get("language", "de")),
            "settings_sync_seconds": browser.get("settings_sync_seconds", 300),
            "visual_watchdog_seconds": browser.get("visual_watchdog_seconds", 45),
        }
        await _fire_command_if_allowed(hass, device_id, "browser_config", browser_payload)

    pages = device.get("pages") or []
    if pages:
        await _fire_command_if_allowed(
            hass,
            device_id,
            "set_pages",
            {
                "pages": pages,
                "duration": device.get("duration", 15),
                "auto_rotate": device.get("auto_rotate", True),
                "pause_on_touch": device.get("pause_on_touch", True),
                "touch_pause": device.get("touch_pause", 30),
            },
        )

    overlays = device.get("overlays") or {}
    if overlays:
        await _fire_command_if_allowed(
            hass,
            device_id,
            "ticker_config",
            {
                "enabled": overlays.get("ticker_enabled", True),
                "fixed_messages": overlays.get("ticker_fixed_messages") or [],
                "messages": overlays.get("ticker_fixed_messages") or [],
                "position": overlays.get("ticker_position", "bottom"),
                "height": overlays.get("ticker_height", 36),
                "auto_hide_seconds": overlays.get("ticker_auto_hide_seconds", 15),
                "color": overlays.get("ticker_color", "#111827"),
                "text_color": overlays.get("ticker_text_color", "#ffffff"),
                "text_size": overlays.get("ticker_text_size", 16),
            },
        )
        if overlays.get("ticker"):
            await _fire_command_if_allowed(hass, device_id, "ticker", {"text": overlays["ticker"], "visible": True})
        if overlays.get("banner_message") or overlays.get("banner"):
            await _fire_command_if_allowed(
                hass,
                device_id,
                "banner",
                {
                    "title": overlays.get("banner_title", "Info"),
                    "message": overlays.get("banner_message") or overlays.get("banner"),
                    "color": overlays.get("banner_color", "#2196F3"),
                    "text_color": overlays.get("banner_text_color", "#ffffff"),
                    "position": overlays.get("banner_position", "top"),
                    "text_size": overlays.get("banner_text_size", 18),
                    "duration": 8,
                },
            )
        if overlays.get("clock_enabled") is True:
            await _fire_command_if_allowed(
                hass,
                device_id,
                "clock",
                {
                    "visible": True,
                    "position": overlays.get("clock_position", "top-right"),
                    "show_date": overlays.get("clock_show_date", True),
                },
            )
        if overlays.get("weather_entity") or overlays.get("weather"):
            await _fire_command_if_allowed(
                hass,
                device_id,
                "weather",
                {
                    "entity_id": overlays.get("weather_entity", ""),
                    "text": overlays.get("weather", ""),
                    "position": overlays.get("weather_position", "top-left"),
                    "layout": overlays.get("weather_layout", "compact"),
                    "duration": overlays.get("weather_duration", 45),
                    "width": overlays.get("weather_width", 280),
                    "height": overlays.get("weather_height", 74),
                    "text_size": overlays.get("weather_text_size", 16),
                    "show_forecast": overlays.get("weather_show_forecast", False),
                    "show_pressure": overlays.get("weather_show_pressure", False),
                    "visible": True,
                },
            )
        if overlays.get("camera_entity") or overlays.get("camera_url") or overlays.get("camera"):
            await _fire_command_if_allowed(
                hass,
                device_id,
                "camera",
                {
                    "entity_id": overlays.get("camera_entity", ""),
                    "url": overlays.get("camera_url") or overlays.get("camera", ""),
                    "position": overlays.get("camera_position", "fullscreen"),
                    "duration": overlays.get("camera_duration", 30),
                    "refresh_seconds": overlays.get("camera_refresh_seconds", 10),
                    "width": overlays.get("camera_width", 360),
                    "height": overlays.get("camera_height", 220),
                    "visible": True,
                },
            )

    media = device.get("media") or {}
    if media:
        await _fire_command_if_allowed(
            hass,
            device_id,
            "audio_config",
            {
                "media_volume": media.get("media_volume", 100),
                "tts_volume": media.get("tts_volume", 100),
                "audio_stream": media.get("audio_stream", "music"),
                "force_max_volume": media.get("force_max_volume", True),
                "audio_focus": media.get("audio_focus", True),
            },
        )

    settings = device.get("device_settings") or {}
    if settings:
        if "brightness" in settings:
            await _fire_command_if_allowed(hass, device_id, "brightness", {"value": settings.get("brightness"), "system": settings.get("brightness_system", False)})
        if "volume" in settings:
            await _fire_command_if_allowed(hass, device_id, "volume", {"value": settings.get("volume"), "stream": "music"})
        if settings.get("orientation"):
            await _fire_command_if_allowed(hass, device_id, "orientation", {"value": settings.get("orientation")})
        if "screen_power" in settings:
            await _fire_command_if_allowed(hass, device_id, "set_screen_power", {"power": bool(settings.get("screen_power"))})
        if "accelerometer_motion" in settings:
            await _fire_command_if_allowed(hass, device_id, "motion_detection", {"enabled": bool(settings.get("accelerometer_motion"))})
        if settings.get("camera_front") is True:
            await _fire_command_if_allowed(hass, device_id, "camera_front", {"enabled": True})
        if "camera_motion" in settings:
            await _fire_command_if_allowed(hass, device_id, "camera_motion_detection", {"enabled": bool(settings.get("camera_motion")), "facing": "front"})
        if "light_sensor" in settings:
            await _fire_command_if_allowed(hass, device_id, "set_device_setting", {"setting": "light_sensor", "value": bool(settings.get("light_sensor"))})


class KioskConfigView(HomeAssistantView):
    url = "/api/ha_android_kiosk/config"
    name = "api:ha_android_kiosk:config"
    requires_auth = True

    async def get(self, request):
        hass: HomeAssistant = request.app["hass"]
        return self.json(await _async_load_config(hass))

    async def post(self, request):
        hass: HomeAssistant = request.app["hass"]
        data = await request.json()
        if not isinstance(data, dict):
            return self.json({"error": "JSON object expected"}, status_code=400)
        saved = await _async_save_config(hass, data)
        for device_id in saved.get("devices", {}):
            _signal_device_registered(hass, device_id)
        return self.json(saved)


class KioskDevicesView(HomeAssistantView):
    url = "/api/ha_android_kiosk/devices"
    name = "api:ha_android_kiosk:devices"
    requires_auth = True

    async def get(self, request):
        hass: HomeAssistant = request.app["hass"]
        config = await _async_load_config(hass)
        return self.json({"devices": config.get("devices", {}), "states": hass.data[DOMAIN].get("device_states", {})})


class KioskRegisterView(HomeAssistantView):
    url = "/api/ha_android_kiosk/register"
    name = "api:ha_android_kiosk:register"
    requires_auth = True

    async def post(self, request):
        hass: HomeAssistant = request.app["hass"]
        data = await request.json()
        device_id = str(data.get("device_id", "")).strip()
        if not device_id:
            return self.json({"error": "device_id is required"}, status_code=400)
        config = await _async_load_config(hass)
        devices = config.setdefault("devices", {})
        existing = devices.get(device_id, {})
        existing.update(
            {
                "device_id": device_id,
                "name": data.get("name") or existing.get("name") or device_id,
                "manufacturer": data.get("manufacturer", existing.get("manufacturer", "")),
                "model": data.get("model", existing.get("model", "")),
                "app_version": data.get("app_version", existing.get("app_version", "")),
                "capabilities": data.get("capabilities", existing.get("capabilities", [])),
                "current_url": data.get("current_url", existing.get("current_url", "")),
                "last_seen": _now(),
                "last_status": "registered",
            }
        )
        existing.setdefault("browser", {"home_url": existing.get("current_url") or "/lovelace/0", "start_url": existing.get("current_url") or "/lovelace/0", "fullscreen": True, "keep_screen_on": True, "reload_seconds": 0, "user_agent": "", "webview_debug": False, "external_auth": True, "hide_header": False, "dashboard_language": data.get("dashboard_language", data.get("app_language", "de")), "settings_sync_seconds": 300, "visual_watchdog_seconds": 45})
        existing.setdefault("browser", {})
        existing["browser"].setdefault("external_auth", True)
        existing["browser"].setdefault("hide_header", False)
        existing["browser"].setdefault("dashboard_language", data.get("dashboard_language", data.get("app_language", "de")))
        existing["browser"].setdefault("settings_sync_seconds", 300)
        existing["browser"].setdefault("visual_watchdog_seconds", 45)
        existing.setdefault("duration", 15)
        existing.setdefault("auto_rotate", True)
        existing.setdefault("pause_on_touch", True)
        existing.setdefault("touch_pause", 30)
        existing.setdefault("pages", [])
        existing.setdefault("overlays", {})
        existing.setdefault("device_settings", {"brightness": 80, "volume": 50, "orientation": "unspecified", "screen_power": True, "accelerometer_motion": False, "camera_front": False, "camera_motion": False, "camera_motion_facing": "front", "light_sensor": True})
        existing.setdefault("media", {"media_url": "", "sound_url": "", "tts_message": "", "tts_language": "de-DE", "media_volume": 100, "tts_volume": 100, "audio_stream": "music", "force_max_volume": True, "audio_focus": True})
        existing["media"].setdefault("media_volume", 100)
        existing["media"].setdefault("tts_volume", 100)
        existing["media"].setdefault("audio_stream", "music")
        existing["media"].setdefault("force_max_volume", True)
        existing["media"].setdefault("audio_focus", True)
        existing.setdefault("background", {"enabled": False, "images": [], "interval": 20, "random": False, "size": "cover", "position": "center", "repeat": "no-repeat", "opacity": 100})
        existing.setdefault(
            "allowed_functions",
            [
                "display",
                "browser",
                "rotation",
                "ticker",
                "alerts",
                "clock",
                "weather",
                "camera_overlay",
                "brightness",
                "volume",
                "orientation",
                "vibrate",
                "camera",
                "camera_front",
                "camera_motion_detection",
                "motion_detection",
                "light_sensor",
                "media_player",
            ],
        )
        devices[device_id] = existing
        await _async_save_config(hass, config)
        _merge_device_state(hass, device_id, {**data, "status": "registered"})
        hass.bus.async_fire(EVENT_STATUS, {"device_id": device_id, "event": "registered", "data": data, "time": _now()})
        _signal_device_registered(hass, device_id)
        return self.json({"ok": True, "device": existing})


class KioskStatusView(HomeAssistantView):
    url = "/api/ha_android_kiosk/status"
    name = "api:ha_android_kiosk:status"
    requires_auth = True

    async def post(self, request):
        hass: HomeAssistant = request.app["hass"]
        data = await request.json()
        device_id = str(data.get("device_id", "")).strip()
        if not device_id:
            return self.json({"error": "device_id is required"}, status_code=400)
        config = await _async_load_config(hass)
        devices = config.setdefault("devices", {})
        device = devices.setdefault(device_id, {"device_id": device_id, "name": data.get("name") or device_id})
        device["last_seen"] = _now()
        device["last_status"] = data.get("status", "online")
        device["current_url"] = data.get("current_url", device.get("current_url", ""))
        device["last_command"] = data.get("command", device.get("last_command", ""))
        await _async_save_config(hass, config)
        _merge_device_state(hass, device_id, data)
        hass.bus.async_fire(EVENT_STATUS, {"device_id": device_id, "event": "status", "data": data, "time": _now()})
        _signal_device_registered(hass, device_id)
        return self.json({"ok": True})


class KioskDeviceConfigView(HomeAssistantView):
    url = r"/api/ha_android_kiosk/device_config/{device_id}"
    name = "api:ha_android_kiosk:device_config"
    requires_auth = True

    async def get(self, request, device_id: str):
        hass: HomeAssistant = request.app["hass"]
        config = await _async_load_config(hass)
        device = (config.get("devices") or {}).get(device_id)
        if not device:
            return self.json({"ok": False, "error": "device not found", "device_id": device_id}, status_code=404)
        return self.json({"ok": True, "device_id": device_id, "device": device})


class KioskDeviceDeleteView(HomeAssistantView):
    url = r"/api/ha_android_kiosk/devices/{device_id}"
    name = "api:ha_android_kiosk:device_delete"
    requires_auth = True

    async def delete(self, request, device_id: str):
        hass: HomeAssistant = request.app["hass"]
        remove_registry = str(request.query.get("remove_registry", "true")).lower() not in {"0", "false", "no"}
        try:
            return self.json(await _async_delete_device(hass, device_id, remove_registry=remove_registry))
        except vol.Invalid as err:
            return self.json({"error": str(err)}, status_code=400)


class KioskCleanupView(HomeAssistantView):
    url = "/api/ha_android_kiosk/cleanup"
    name = "api:ha_android_kiosk:cleanup"
    requires_auth = True

    async def post(self, request):
        hass: HomeAssistant = request.app["hass"]
        data = await request.json()
        if not isinstance(data, dict):
            return self.json({"error": "JSON object expected"}, status_code=400)
        devices = data.get("devices") or []
        if isinstance(devices, str):
            devices = [x.strip() for x in devices.split(",") if x.strip()]
        remove_registry = bool(data.get("remove_registry", True))
        results = []
        for device_id in devices:
            try:
                results.append(await _async_delete_device(hass, str(device_id), remove_registry=remove_registry))
            except Exception as err:
                results.append({"ok": False, "device_id": str(device_id), "error": str(err)})
        return self.json({"ok": True, "results": results})




def _background_root_dir(hass: HomeAssistant) -> Path:
    path = Path(hass.config.path("www", "ha_android_kiosk", "backgrounds"))
    path.mkdir(parents=True, exist_ok=True)
    return path


def _safe_album_name(name: str | None) -> str:
    raw = (name or "default").strip()
    safe = re.sub(r"[^A-Za-z0-9_.-]+", "_", raw)
    safe = safe.strip("._") or "default"
    return safe[:80]


def _background_dir(hass: HomeAssistant, album: str | None = None) -> Path:
    root = _background_root_dir(hass)
    safe_album = _safe_album_name(album)
    path = root if safe_album == "default" else root / safe_album
    path.mkdir(parents=True, exist_ok=True)
    return path


def _safe_filename(name: str) -> str:
    name = re.sub(r"[^A-Za-z0-9_.-]+", "_", name or "background")
    name = name.strip("._") or "background"
    return name[:120]


def _is_background_file(path: Path) -> bool:
    return path.is_file() and path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp", ".gif"}


def _background_entry(path: Path, album: str = "default") -> dict[str, Any]:
    mime, _ = mimetypes.guess_type(path.name)
    album_safe = _safe_album_name(album)
    prefix = "/local/ha_android_kiosk/backgrounds" if album_safe == "default" else f"/local/ha_android_kiosk/backgrounds/{album_safe}"
    return {
        "name": path.name,
        "album": album_safe,
        "url": f"{prefix}/{path.name}",
        "size": path.stat().st_size,
        "content_type": mime or "image/*",
    }


def _list_background_albums(hass: HomeAssistant) -> list[dict[str, Any]]:
    root = _background_root_dir(hass)
    albums: dict[str, dict[str, Any]] = {"default": {"id": "default", "name": "default", "count": 0, "files": []}}

    for path in sorted(root.iterdir(), key=lambda p: p.name.casefold()):
        if _is_background_file(path):
            entry = _background_entry(path, "default")
            albums["default"]["files"].append(entry)
            albums["default"]["count"] += 1
        elif path.is_dir():
            album = _safe_album_name(path.name)
            info = albums.setdefault(album, {"id": album, "name": album, "count": 0, "files": []})
            for file_path in sorted(path.iterdir(), key=lambda p: p.name.casefold()):
                if _is_background_file(file_path):
                    info["files"].append(_background_entry(file_path, album))
                    info["count"] += 1

    return sorted(albums.values(), key=lambda a: (a["id"] != "default", a["id"].casefold()))


class KioskBackgroundsView(HomeAssistantView):
    url = "/api/ha_android_kiosk/backgrounds"
    name = "api:ha_android_kiosk:backgrounds"
    requires_auth = True

    async def get(self, request):
        hass: HomeAssistant = request.app["hass"]
        album = request.query.get("album") or None
        albums = _list_background_albums(hass)
        if album:
            album_safe = _safe_album_name(album)
            files = next((a["files"] for a in albums if a["id"] == album_safe), [])
        else:
            files = [file for a in albums for file in a["files"]]
        return self.json({"files": files, "albums": [{k: v for k, v in a.items() if k != "files"} for a in albums]})


class KioskBackgroundAlbumsView(HomeAssistantView):
    url = "/api/ha_android_kiosk/backgrounds/albums"
    name = "api:ha_android_kiosk:background_albums"
    requires_auth = True

    async def get(self, request):
        hass: HomeAssistant = request.app["hass"]
        albums = _list_background_albums(hass)
        return self.json({"albums": [{k: v for k, v in a.items() if k != "files"} for a in albums]})

    async def post(self, request):
        hass: HomeAssistant = request.app["hass"]
        data = await request.json()
        album = _safe_album_name(str(data.get("album") or data.get("name") or "default"))
        folder = _background_dir(hass, album)
        return self.json({"ok": True, "album": album, "path": str(folder)})


class KioskBackgroundAlbumView(HomeAssistantView):
    url = r"/api/ha_android_kiosk/backgrounds/albums/{album}"
    name = "api:ha_android_kiosk:background_album"
    requires_auth = True

    async def delete(self, request, album: str):
        hass: HomeAssistant = request.app["hass"]
        album_safe = _safe_album_name(album)
        if album_safe == "default":
            return self.json({"ok": False, "error": "default album cannot be deleted"}, status_code=400)
        folder = _background_dir(hass, album_safe)
        remove_files = request.query.get("remove_files", "true").lower() != "false"
        if folder.exists() and folder.is_dir():
            if remove_files:
                for file_path in folder.iterdir():
                    if file_path.is_file():
                        file_path.unlink()
                folder.rmdir()
            else:
                return self.json({"ok": False, "error": "remove_files=false is not supported yet"}, status_code=400)
        # Remove references from stored device configs.
        cfg = await _async_load_config(hass)
        changed = False
        prefix = f"/local/ha_android_kiosk/backgrounds/{album_safe}/"
        for device in (cfg.get("devices") or {}).values():
            bg = device.get("background") or {}
            if bg.get("album") == album_safe:
                bg["album"] = "default"
                changed = True
            if isinstance(bg.get("images"), list):
                new_images = [url for url in bg["images"] if not str(url).startswith(prefix)]
                if len(new_images) != len(bg["images"]):
                    bg["images"] = new_images
                    changed = True
        if changed:
            await _async_save_config(hass, cfg)
        return self.json({"ok": True, "deleted": album_safe})


class KioskBackgroundUploadView(HomeAssistantView):
    url = "/api/ha_android_kiosk/backgrounds/upload"
    name = "api:ha_android_kiosk:background_upload"
    requires_auth = True

    async def post(self, request):
        hass: HomeAssistant = request.app["hass"]
        reader = await request.multipart()
        saved: list[dict[str, Any]] = []
        album = "default"
        file_parts = []
        # aiohttp multipart parts stream sequentially; capture album fields before saving, but also
        # allow album to arrive after files by storing the bytes temporarily in memory per chunk.
        while True:
            part = await reader.next()
            if part is None:
                break
            if part.name == "album":
                album = _safe_album_name((await part.text()).strip() or "default")
                continue
            if part.name not in {"file", "files"}:
                continue
            filename = _safe_filename(part.filename or "background.jpg")
            content = bytearray()
            while chunk := await part.read_chunk():
                content.extend(chunk)
            file_parts.append((filename, bytes(content)))

        folder = _background_dir(hass, album)
        for filename, content in file_parts:
            if not filename.lower().endswith((".jpg", ".jpeg", ".png", ".webp", ".gif")):
                filename += ".jpg"
            path = folder / filename
            stem, suffix = path.stem, path.suffix
            counter = 1
            while path.exists():
                path = folder / f"{stem}_{counter}{suffix}"
                counter += 1
            path.write_bytes(content)
            saved.append(_background_entry(path, album))
        return self.json({"ok": True, "album": _safe_album_name(album), "files": saved})


class KioskBackgroundDeleteView(HomeAssistantView):
    url = r"/api/ha_android_kiosk/backgrounds/{filename}"
    name = "api:ha_android_kiosk:background_delete"
    requires_auth = True

    async def delete(self, request, filename: str):
        hass: HomeAssistant = request.app["hass"]
        album = request.query.get("album") or "default"
        safe = _safe_filename(filename)
        path = _background_dir(hass, album) / safe
        if path.exists() and path.is_file():
            path.unlink()
            return self.json({"ok": True, "deleted": safe, "album": _safe_album_name(album)})
        return self.json({"ok": False, "error": "file not found"}, status_code=404)


class KioskBackgroundAlbumFileDeleteView(HomeAssistantView):
    url = r"/api/ha_android_kiosk/backgrounds/albums/{album}/files/{filename}"
    name = "api:ha_android_kiosk:background_album_file_delete"
    requires_auth = True

    async def delete(self, request, album: str, filename: str):
        hass: HomeAssistant = request.app["hass"]
        safe = _safe_filename(filename)
        path = _background_dir(hass, album) / safe
        if path.exists() and path.is_file():
            path.unlink()
            return self.json({"ok": True, "deleted": safe, "album": _safe_album_name(album)})
        return self.json({"ok": False, "error": "file not found"}, status_code=404)


class KioskCommandView(HomeAssistantView):
    url = "/api/ha_android_kiosk/command"
    name = "api:ha_android_kiosk:command"
    requires_auth = True

    async def post(self, request):
        hass: HomeAssistant = request.app["hass"]
        data = await request.json()
        device_id = str(data.get("device_id", "")).strip()
        command = str(data.get("command", "")).strip()
        if not device_id or not command:
            return self.json({"error": "device_id and command are required"}, status_code=400)
        try:
            if command == "apply_profile":
                await _apply_profile(hass, device_id)
                return self.json({"ok": True, "applied": True})
            payload = data.get("payload") or {}
            if not isinstance(payload, dict):
                return self.json({"error": "payload must be an object"}, status_code=400)
            if command in {"weather", "show_weather"}:
                payload = _enrich_weather_payload(hass, payload)
            elif command in {"status_overlay", "show_status_overlay"}:
                payload = _build_status_overlay_payload(hass, payload)
            elif command in {"overlay_sequence", "show_overlay_sequence"}:
                payload = _build_overlay_sequence_payload(hass, payload)
            sent = await _fire_command(hass, device_id, command, payload)
            return self.json({"ok": True, "sent": sent})
        except vol.Invalid as err:
            return self.json({"error": str(err)}, status_code=403)
