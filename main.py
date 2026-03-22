#!/usr/bin/env python3
"""
SINproxy Entry Point
"""
import sys
import os

# Add the current directory to sys.path to ensure sinproxy package is findable
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

try:
    from sinproxy.cli import main
except ImportError as e:
    print(f"Error: Could not import sinproxy package. {e}")
    sys.exit(1)

if __name__ == "__main__":
    main()
