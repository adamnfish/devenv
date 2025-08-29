"""
Entry point for devenv CLI
"""

import sys
from .cli import cli


def main():
    """Main entry point"""
    return cli()


if __name__ == "__main__":
    sys.exit(main())