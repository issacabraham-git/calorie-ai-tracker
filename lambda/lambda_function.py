import json
import os
import boto3
import urllib.request
import urllib.error
from datetime import datetime
from boto3.dynamodb.conditions import Key

dynamodb = boto3.resource('dynamodb')
logs_table = dynamodb.Table('CalorieLogs')
profiles_table = dynamodb.Table('UserProfiles')

GEMINI_API_KEY = os.environ['GEMINI_API_KEY']
GEMINI_URL = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key={GEMINI_API_KEY}"


def call_gemini(food_description: str) -> str:
    prompt = f"""You are a nutritionist.
The user provided this description: "{food_description}".
Estimate Calories, Protein(g), Carbs(g), and Fat(g).
Output strictly in this CSV format per line: Name,Calories,Protein,Carbs,Fat
Example: 2 Porotta,450,10g,60g,15g
No headers. No markdown. No explanation."""

    body = json.dumps({
        "contents": [{"parts": [{"text": prompt}]}]
    }).encode("utf-8")

    req = urllib.request.Request(
        GEMINI_URL,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    with urllib.request.urlopen(req) as resp:
        result = json.loads(resp.read().decode("utf-8"))
        return result["candidates"][0]["content"]["parts"][0]["text"]


def parse_csv_response(raw: str) -> list:
    items = []
    for line in raw.strip().splitlines():
        parts = [p.strip() for p in line.split(",")]
        if len(parts) >= 5:
            items.append({
                "name": parts[0],
                "calories": parts[1],
                "protein": parts[2],
                "carbs": parts[3],
                "fat": parts[4]
            })
    return items


def save_to_dynamodb(user_id: str, food_items: list, meal_type: str, date_string: str):
    for item in food_items:
        entry_id = str(datetime.utcnow().timestamp()) + "_" + item["name"].replace(" ", "_")
        logs_table.put_item(Item={
            "userId": user_id,
            "id": entry_id,
            "name": item["name"],
            "calories": item["calories"],
            "protein": item["protein"],
            "carbs": item["carbs"],
            "fat": item["fat"],
            "mealType": meal_type,
            "dateString": date_string
        })


def lambda_handler(event, context):
    # CORS preflight
    if event.get("requestContext", {}).get("http", {}).get("method") == "OPTIONS":
        return {
            "statusCode": 200,
            "headers": {
                "Access-Control-Allow-Origin": "*",
                "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
                "Access-Control-Allow-Headers": "Content-Type"
            },
            "body": ""
        }

    try:
        body = json.loads(event.get("body", "{}"))
        action = body.get("action", "")

        # --- ANALYZE FOOD ---
        if action == "analyze_food":
            food_description = body.get("foodDescription", "")
            meal_type = body.get("mealType", "Snack")
            date_string = body.get("dateString", "")
            user_id = body.get("userId", "anonymous")

            if not food_description:
                return response(400, {"error": "foodDescription is required"})

            raw = call_gemini(food_description)
            items = parse_csv_response(raw)

            if date_string and user_id != "anonymous":
                save_to_dynamodb(user_id, items, meal_type, date_string)

            return response(200, {"success": True, "items": items})

        # --- GET FOOD LOGS ---
        elif action == "get_logs":
            user_id = body.get("userId", "anonymous")
            result = logs_table.query(
                KeyConditionExpression=Key("userId").eq(user_id)
            )
            return response(200, {"success": True, "logs": result.get("Items", [])})

        # --- DELETE FOOD ENTRY ---
        elif action == "delete_entry":
            user_id = body.get("userId", "anonymous")
            entry_id = body.get("id", "")
            if not entry_id:
                return response(400, {"error": "id is required"})
            logs_table.delete_item(Key={"userId": user_id, "id": entry_id})
            return response(200, {"success": True})

        # --- SAVE USER PROFILE ---
        elif action == "save_profile":
            user_id = body.get("userId", "anonymous")
            profile = body.get("profile", {})
            if not profile or user_id == "anonymous":
                return response(400, {"error": "userId and profile are required"})

            profiles_table.put_item(Item={
                "userId": user_id,
                "weightKg": str(profile.get("weightKg", "0")),
                "heightCm": str(profile.get("heightCm", "0")),
                "age": str(profile.get("age", "0")),
                "isMale": str(profile.get("isMale", "true")),
                "activityLevel": str(profile.get("activityLevel", "1.2")),
                "goal": profile.get("goal", "Maintain"),
                "dailyCalorieTarget": str(profile.get("dailyCalorieTarget", "2000"))
            })
            return response(200, {"success": True})

        # --- GET USER PROFILE ---
        elif action == "get_profile":
            user_id = body.get("userId", "anonymous")
            result = profiles_table.get_item(Key={"userId": user_id})
            item = result.get("Item")
            if not item:
                return response(200, {"success": True, "profile": None})
            return response(200, {"success": True, "profile": item})

        else:
            return response(400, {"error": f"Unknown action: {action}"})

    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        return response(500, {"error": f"Gemini API error: {error_body}"})
    except Exception as e:
        return response(500, {"error": str(e)})


def response(status_code: int, body: dict) -> dict:
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
        },
        "body": json.dumps(body)
    }
