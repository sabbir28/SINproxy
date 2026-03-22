import datetime
import sys

def log(message: str):
    """Professional timestamped logger with Unicode safety"""
    timestamp = datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]
    output = f"[{timestamp}] {message}"
    try:
        print(output)
    except UnicodeEncodeError:
        # Fallback for terminals that don't support certain characters (like box-drawing on Windows)
        print(output.encode('ascii', errors='replace').decode('ascii'))
    sys.stdout.flush()

def print_ts(message: str):
    """Alias for log used in some modules"""
    log(message)
