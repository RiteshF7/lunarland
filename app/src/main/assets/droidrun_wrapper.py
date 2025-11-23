"""
DroidRun Android Wrapper - Python Tools Implementation

This module provides a Python implementation of the Tools interface
that communicates with Android native code via StateBridge (file-based JSON).
"""

import json
import os
import time
import asyncio
import logging
from typing import Dict, Any, List, Tuple, Optional
from datetime import datetime

# Import DroidRun agent components
from droidrun.tools.tools import Tools
from droidrun.agent.droid import DroidAgent
from droidrun.config_manager import DroidrunConfig

logger = logging.getLogger(__name__)


class AndroidToolsWrapper(Tools):
    """
    Python wrapper for AndroidTools that communicates via StateBridge.
    
    This class implements the Tools interface and bridges Python agent calls
    to native Android device control via JSON file-based communication.
    """
    
    def __init__(
        self,
        state_file: str,
        actions_file: str,
        result_file: str,
        poll_interval: float = 0.1
    ):
        """
        Initialize AndroidToolsWrapper.
        
        Args:
            state_file: Path to device state JSON file (read by Python)
            actions_file: Path to Python actions JSON file (written by Python, read by Android)
            result_file: Path to action result JSON file (written by Android, read by Python)
            poll_interval: Polling interval in seconds for state/result updates
        """
        self.state_file = state_file
        self.actions_file = actions_file
        self.result_file = result_file
        self.poll_interval = poll_interval
        
        # State cache
        self.clickable_elements_cache: List[Dict[str, Any]] = []
        self.memory: List[str] = []
        self.success: Optional[bool] = None
        self.reason: Optional[str] = None
        self.finished: bool = False
        self.save_trajectories: str = "none"
        
        # Context for event streaming (if needed)
        self._ctx = None
        
        logger.info(f"AndroidToolsWrapper initialized")
        logger.info(f"State file: {state_file}")
        logger.info(f"Actions file: {actions_file}")
        logger.info(f"Result file: {result_file}")
    
    def _set_context(self, ctx):
        """Set context for event streaming."""
        self._ctx = ctx
    
    async def get_state(self) -> Tuple[str, str, List[Dict[str, Any]], Dict[str, Any]]:
        """
        Get device state from Android via StateBridge.
        
        Returns:
            Tuple of (formatted_text, focused_text, a11y_tree, phone_state)
        """
        try:
            # Read state from JSON file
            max_retries = 10
            for attempt in range(max_retries):
                try:
                    if os.path.exists(self.state_file):
                        with open(self.state_file, 'r') as f:
                            state_data = json.load(f)
                        
                        formatted_text = state_data.get("formattedText", "")
                        focused_text = state_data.get("focusedText", "")
                        a11y_tree = state_data.get("a11yTree", [])
                        phone_state = state_data.get("phoneState", {})
                        
                        # Update cache
                        self.clickable_elements_cache = a11y_tree
                        
                        logger.debug(f"State retrieved: {len(a11y_tree)} elements")
                        return (formatted_text, focused_text, a11y_tree, phone_state)
                    else:
                        # File doesn't exist yet, wait a bit
                        await asyncio.sleep(self.poll_interval)
                except json.JSONDecodeError:
                    # File might be partially written, wait and retry
                    await asyncio.sleep(self.poll_interval)
                    continue
            
            # If we get here, state file wasn't available
            logger.warning("State file not available after retries")
            empty_phone_state = {
                "packageName": "Unknown",
                "currentApp": "Unknown",
                "activityName": "Unknown",
                "keyboardVisible": False,
                "isEditable": False,
                "focusedElement": {}
            }
            return ("State not available", "", [], empty_phone_state)
            
        except Exception as e:
            logger.error(f"Error getting state: {e}")
            empty_phone_state = {
                "packageName": "Unknown",
                "currentApp": "Unknown",
                "activityName": "Unknown",
                "keyboardVisible": False,
                "isEditable": False,
                "focusedElement": {}
            }
            return (f"Error: {e}", "", [], empty_phone_state)
    
    async def get_date(self) -> str:
        """Get current date on device."""
        # Android will provide the date, but for now return Python date
        return datetime.now().strftime("%a %b %d %H:%M:%S %Z %Y")
    
    def _extract_element_coordinates_by_index(self, index: int) -> Tuple[int, int]:
        """Extract coordinates from element by index."""
        def find_element_by_index(elements, target_index):
            for item in elements:
                if item.get("index") == target_index:
                    return item
                children = item.get("children", [])
                if children:
                    result = find_element_by_index(children, target_index)
                    if result:
                        return result
            return None
        
        if not self.clickable_elements_cache:
            raise ValueError("No UI elements cached. Call get_state first.")
        
        element = find_element_by_index(self.clickable_elements_cache, index)
        if not element:
            raise ValueError(f"No element found with index {index}")
        
        bounds_str = element.get("bounds")
        if not bounds_str:
            raise ValueError(f"Element with index {index} has no bounds")
        
        bounds = [int(x) for x in bounds_str.split(",")]
        if len(bounds) != 4:
            raise ValueError(f"Invalid bounds format: {bounds_str}")
        
        left, top, right, bottom = bounds
        x = (left + right) // 2
        y = (top + bottom) // 2
        
        return x, y
    
    @Tools.ui_action
    async def tap_by_index(self, index: int) -> str:
        """Tap element by index."""
        return await self._execute_action("tap_by_index", {"index": str(index)})
    
    @Tools.ui_action
    async def swipe(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration_ms: int = 300
    ) -> bool:
        """Swipe gesture."""
        result = await self._execute_action("swipe", {
            "start_x": str(start_x),
            "start_y": str(start_y),
            "end_x": str(end_x),
            "end_y": str(end_y),
            "duration_ms": str(duration_ms)
        })
        return result.get("success", False) if isinstance(result, dict) else False
    
    @Tools.ui_action
    async def drag(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration_ms: int = 3000
    ) -> bool:
        """Drag gesture."""
        result = await self._execute_action("drag", {
            "start_x": str(start_x),
            "start_y": str(start_y),
            "end_x": str(end_x),
            "end_y": str(end_y),
            "duration_ms": str(duration_ms)
        })
        return result.get("success", False) if isinstance(result, dict) else False
    
    @Tools.ui_action
    async def input_text(self, text: str, index: int = -1, clear: bool = False) -> str:
        """Input text."""
        return await self._execute_action("input_text", {
            "text": text,
            "index": str(index),
            "clear": str(clear)
        })
    
    @Tools.ui_action
    async def back(self) -> str:
        """Press back button."""
        return await self._execute_action("back", {})
    
    @Tools.ui_action
    async def press_key(self, keycode: int) -> str:
        """Press key by keycode."""
        return await self._execute_action("press_key", {"keycode": str(keycode)})
    
    @Tools.ui_action
    async def start_app(self, package: str, activity: str = "") -> str:
        """Start app."""
        return await self._execute_action("start_app", {
            "package": package,
            "activity": activity
        })
    
    async def take_screenshot(self) -> Tuple[str, bytes]:
        """Take screenshot."""
        result = await self._execute_action("take_screenshot", {})
        if isinstance(result, dict) and "image_base64" in result:
            import base64
            image_bytes = base64.b64decode(result["image_base64"])
            return ("PNG", image_bytes)
        else:
            raise ValueError("Screenshot failed")
    
    async def list_packages(self, include_system_apps: bool = False) -> List[str]:
        """List packages."""
        result = await self._execute_action("list_packages", {
            "include_system_apps": str(include_system_apps)
        })
        if isinstance(result, dict) and "packages" in result:
            return result["packages"]
        return []
    
    async def get_apps(self, include_system: bool = True) -> List[Dict[str, str]]:
        """Get apps."""
        result = await self._execute_action("get_apps", {
            "include_system": str(include_system)
        })
        if isinstance(result, dict) and "apps" in result:
            return result["apps"]
        return []
    
    def remember(self, information: str) -> str:
        """Remember information."""
        if not information or not isinstance(information, str):
            return "Error: Please provide valid information to remember."
        
        self.memory.append(information.strip())
        
        max_memory_items = 10
        if len(self.memory) > max_memory_items:
            self.memory = self.memory[-max_memory_items:]
        
        return f"Remembered: {information}"
    
    async def get_memory(self) -> List[str]:
        """Get memory."""
        return self.memory.copy()
    
    @Tools.ui_action
    async def complete(self, success: bool, reason: str = "") -> None:
        """Complete task."""
        self.success = success
        self.reason = reason or ("Task completed successfully." if success else "Task failed.")
        self.finished = True
        
        # Also notify Android
        await self._execute_action("complete", {
            "success": str(success),
            "reason": reason
        })
    
    async def _execute_action(
        self,
        action_type: str,
        parameters: Dict[str, str]
    ) -> Any:
        """
        Execute an action by writing to actions file and waiting for result.
        
        Args:
            action_type: Type of action to execute
            parameters: Action parameters
            
        Returns:
            Action result (parsed from JSON)
        """
        import uuid
        
        action_id = str(uuid.uuid4())
        
        # Write action to file
        action_data = {
            "actionType": action_type,
            "parameters": parameters,
            "timestamp": int(time.time() * 1000),
            "actionId": action_id
        }
        
        try:
            with open(self.actions_file, 'w') as f:
                json.dump(action_data, f)
            
            logger.debug(f"Action written: {action_type} (id: {action_id})")
            
            # Wait for result
            max_wait_time = 30.0  # 30 seconds timeout
            start_time = time.time()
            
            while time.time() - start_time < max_wait_time:
                if os.path.exists(self.result_file):
                    try:
                        with open(self.result_file, 'r') as f:
                            result_data = json.load(f)
                        
                        if result_data.get("actionId") == action_id:
                            # Result found, delete file and return
                            os.remove(self.result_file)
                            
                            if result_data.get("success"):
                                # Parse result
                                result_str = result_data.get("result", "")
                                try:
                                    return json.loads(result_str)
                                except (json.JSONDecodeError, TypeError):
                                    return result_str
                            else:
                                error = result_data.get("error", "Unknown error")
                                raise Exception(f"Action failed: {error}")
                    except (json.JSONDecodeError, KeyError):
                        # File might be partially written or for different action
                        pass
                
                await asyncio.sleep(self.poll_interval)
            
            raise TimeoutError(f"Action {action_type} timed out after {max_wait_time}s")
            
        except Exception as e:
            logger.error(f"Error executing action {action_type}: {e}")
            raise


# Main entry point for running agent
async def main():
    """Main entry point for Python agent execution."""
    import sys
    
    # Read environment variables
    goal = os.environ.get("DROIDRUN_GOAL", "")
    config_path = os.environ.get("DROIDRUN_CONFIG", "")
    state_file = os.environ.get("DROIDRUN_STATE_FILE", "")
    actions_file = os.environ.get("DROIDRUN_ACTIONS_FILE", "")
    result_file = os.environ.get("DROIDRUN_RESULT_FILE", "")
    
    if not goal:
        print("Error: DROIDRUN_GOAL not set", file=sys.stderr)
        sys.exit(1)
    
    if not config_path:
        print("Error: DROIDRUN_CONFIG not set", file=sys.stderr)
        sys.exit(1)
    
    # Load config
    try:
        config = DroidrunConfig.from_yaml(config_path)
    except Exception as e:
        print(f"Error loading config: {e}", file=sys.stderr)
        sys.exit(1)
    
    # Create tools wrapper
    tools = AndroidToolsWrapper(state_file, actions_file, result_file)
    
    # Create and run agent
    try:
        agent = DroidAgent(goal=goal, config=config, tools=tools)
        result = await agent.run()
        print(json.dumps({"type": "complete", "success": True, "result": str(result)}))
    except Exception as e:
        print(json.dumps({"type": "error", "message": str(e)}), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())

