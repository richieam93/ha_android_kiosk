from __future__ import annotations

from homeassistant.components.media_player import MediaPlayerEntity, MediaPlayerEntityFeature, MediaPlayerState, MediaType
from homeassistant.components.media_player.browse_media import BrowseMedia
try:
    from homeassistant.components import media_source
except Exception:  # pragma: no cover - media_source is part of HA default_config on normal systems
    media_source = None
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect, async_dispatcher_send
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from . import _async_load_config, _fire_command
from .const import DOMAIN, SIGNAL_DEVICE_REGISTERED, SIGNAL_DEVICE_STATE_UPDATED
from .entity_base import KioskEntity


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry, async_add_entities: AddEntitiesCallback) -> None:
    added: set[str] = set()

    async def add_device(device_id: str) -> None:
        if device_id in added:
            return
        config = await _async_load_config(hass)
        if device_id not in config.get("devices", {}):
            return
        added.add(device_id)
        async_add_entities([KioskMediaPlayer(hass, device_id)])

    config = await _async_load_config(hass)
    for device_id in config.get("devices", {}):
        await add_device(device_id)

    @callback
    def _new_device(device_id: str) -> None:
        hass.async_create_task(add_device(device_id))

    entry.async_on_unload(async_dispatcher_connect(hass, SIGNAL_DEVICE_REGISTERED, _new_device))


class KioskMediaPlayer(KioskEntity, MediaPlayerEntity):
    _attr_name = "Medienplayer"
    _attr_supported_features = (
        MediaPlayerEntityFeature.PLAY
        | MediaPlayerEntityFeature.PAUSE
        | MediaPlayerEntityFeature.STOP
        | MediaPlayerEntityFeature.VOLUME_SET
        | MediaPlayerEntityFeature.VOLUME_MUTE
        | MediaPlayerEntityFeature.PLAY_MEDIA
        | MediaPlayerEntityFeature.NEXT_TRACK
        | MediaPlayerEntityFeature.PREVIOUS_TRACK
        | MediaPlayerEntityFeature.SEEK
        | MediaPlayerEntityFeature.BROWSE_MEDIA
    )

    def __init__(self, hass: HomeAssistant, device_id: str) -> None:
        super().__init__(hass, device_id, "media_player")

    @property
    def state(self) -> MediaPlayerState:
        raw = str(self.device_state.get("media_state") or "idle").lower()
        if raw == "playing":
            return MediaPlayerState.PLAYING
        if raw == "paused":
            return MediaPlayerState.PAUSED
        if raw == "buffering":
            return MediaPlayerState.BUFFERING
        return MediaPlayerState.IDLE

    @property
    def volume_level(self) -> float | None:
        value = self.device_state.get("volume")
        if value is None:
            return None
        try:
            return max(0.0, min(1.0, float(value) / 100.0))
        except (TypeError, ValueError):
            return None

    @property
    def is_volume_muted(self) -> bool | None:
        value = self.device_state.get("media_muted")
        if value is None:
            return False
        return bool(value)

    async def async_media_play(self) -> None:
        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_state"] = "playing"
        await _fire_command(self.hass, self._device_id, "media_play", {})
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)

    async def async_media_pause(self) -> None:
        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_state"] = "paused"
        await _fire_command(self.hass, self._device_id, "media_pause", {})
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)

    async def async_media_stop(self) -> None:
        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_state"] = "idle"
        await _fire_command(self.hass, self._device_id, "media_stop", {})
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)

    async def async_set_volume_level(self, volume: float) -> None:
        pct = int(max(0.0, min(1.0, volume)) * 100)
        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["volume"] = pct
        await _fire_command(self.hass, self._device_id, "volume", {"value": pct, "stream": "music"})
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)

    async def async_mute_volume(self, mute: bool) -> None:
        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_muted"] = mute
        await _fire_command(self.hass, self._device_id, "media_mute", {"muted": mute})
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)

    @property
    def media_position(self) -> int | None:
        value = self.device_state.get("media_position")
        try:
            return int(value) if value is not None else None
        except (TypeError, ValueError):
            return None

    @property
    def media_duration(self) -> int | None:
        value = self.device_state.get("media_duration")
        try:
            return int(value) if value is not None else None
        except (TypeError, ValueError):
            return None

    @property
    def media_title(self) -> str | None:
        return self.device_state.get("media_title") or self.device_state.get("media_url")

    async def async_media_next_track(self) -> None:
        await _fire_command(self.hass, self._device_id, "media_next", {})

    async def async_media_previous_track(self) -> None:
        await _fire_command(self.hass, self._device_id, "media_previous", {})

    async def async_media_seek(self, position: float) -> None:
        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_position"] = int(position)
        await _fire_command(self.hass, self._device_id, "media_seek", {"position": int(position)})
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)

    async def async_browse_media(
        self,
        media_content_type: str | None = None,
        media_content_id: str | None = None,
    ) -> BrowseMedia:
        """Expose Home Assistant's Media Browser for this Android player."""
        if media_source is None:
            raise RuntimeError("media_source integration is not available")

        def _filter(item) -> bool:
            content_type = str(getattr(item, "media_content_type", "") or "")
            media_class = str(getattr(item, "media_class", "") or "")
            # Allow folders/apps/providers and common playable audio/video items.
            if content_type in {"app", "directory"}:
                return True
            if media_class in {"app", "directory", "album", "playlist", "channel"}:
                return True
            return (
                content_type.startswith("audio/")
                or content_type.startswith("video/")
                or content_type in {"music", "audio", "video", "playlist"}
            )

        return await media_source.async_browse_media(
            self.hass,
            media_content_id,
            content_filter=_filter,
        )

    async def async_play_media(self, media_type: MediaType | str, media_id: str, **kwargs) -> None:
        play_url = media_id
        play_type = str(media_type)
        if media_source is not None and isinstance(media_id, str) and media_id.startswith("media-source://"):
            try:
                resolved = await media_source.async_resolve_media(self.hass, media_id, self.entity_id)
                play_url = resolved.url
                play_type = getattr(resolved, "mime_type", None) or play_type
            except Exception as err:
                self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_state"] = "error"
                self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_error"] = str(err)
                async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)
                raise

        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_state"] = "buffering"
        self.hass.data[DOMAIN].setdefault("device_states", {}).setdefault(self._device_id, {})["media_url"] = play_url
        await _fire_command(self.hass, self._device_id, "play_media", {
            "media_content_type": play_type,
            "media_content_id": media_id,
            "url": play_url,
        })
        async_dispatcher_send(self.hass, SIGNAL_DEVICE_STATE_UPDATED, self._device_id)
