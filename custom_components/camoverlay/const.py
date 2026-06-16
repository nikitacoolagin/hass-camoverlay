"""Constants for the CamOverlay integration."""

DOMAIN = "camoverlay"

CONF_PREFIX = "prefix"
CONF_CAMERA = "camera_entity"

DEFAULT_PREFIX = "camoverlay"

# MQTT subtopics (relative to "<prefix>/<device>/")
TOPIC_CMD = "cmd"
TOPIC_AVAILABILITY = "availability"
TOPIC_STATE = "state"

PAYLOAD_ONLINE = "online"
PAYLOAD_OFFLINE = "offline"

# PiP geometry
SIZE_QUARTER = "quarter"  # 1/2 of screen width  (~1/4 area)
SIZE_SMALL = "small"      # 1/4 of screen width  (~1/16 area)

SIZES = [SIZE_QUARTER, SIZE_SMALL]

# user-facing size labels <-> internal value
SIZE_LABELS = {
    SIZE_QUARTER: "1/4",
    SIZE_SMALL: "1/16",
}
SIZE_FROM_LABEL = {v: k for k, v in SIZE_LABELS.items()}

CORNERS = ["tl", "tr", "bl", "br"]
CORNER_LABELS = {
    "tl": "top-left",
    "tr": "top-right",
    "bl": "bottom-left",
    "br": "bottom-right",
}
CORNER_FROM_LABEL = {v: k for k, v in CORNER_LABELS.items()}

DEFAULT_SIZE = SIZE_QUARTER
DEFAULT_CORNER = "br"

# dispatcher signals
SIGNAL_NEW_DEVICE = f"{DOMAIN}_new_device"
SIGNAL_DEVICE_UPDATE = f"{DOMAIN}_device_update_{{}}"

# services
SERVICE_FULLSCREEN = "fullscreen"
SERVICE_PIP = "pip"
SERVICE_STOP = "stop"

ATTR_DEVICE = "device"
ATTR_SIZE = "size"
ATTR_CORNER = "corner"

PLATFORMS = ["binary_sensor", "sensor", "button", "select"]
