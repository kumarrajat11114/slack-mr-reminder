import os
import schedule
import time
from datetime import datetime
from slack_bolt import App
from slack_bolt.adapter.socket_mode import SocketModeHandler
from github import Github
from github import GithubException
from gitlab import Gitlab
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv("/Users/rajatkumar/Documents/Personal_workspace/mr-reminder-slackbot/enviroment.env")

# Initialize Slack app with bot token
app = App(token=os.environ["SLACK_BOT_TOKEN"])

# Configuration
TARGET_BRANCH = os.environ["TARGET_BRANCH"]
REPOSITORY = os.environ.get('GIT_REPOSITORY')  # e.g., 'owner/repo'
GIT_URL = os.environ['GIT_URL']

# Initialize Git client
git = (Github(os.environ['GIT_ACCESS_TOKEN'])) if "github" in GIT_URL else (Gitlab(os.environ['GIT_URL'], private_token=os.environ['GIT_ACCESS_TOKEN'])) 

def format_duration(days):
    """
    Format the duration in a more readable way
    """
    if days == 0:
        return "today"
    elif days == 1:
        return "1 day"
    elif days < 7:
        return f"{days} days"
    elif days < 30:
        weeks = days // 7
        remaining_days = days % 7
        if remaining_days == 0:
            return f"{weeks} {'week' if weeks == 1 else 'weeks'}"
        return f"{weeks} {'week' if weeks == 1 else 'weeks'} and {remaining_days} {'day' if remaining_days == 1 else 'days'}"
    else:
        months = days // 30
        remaining_days = days % 30
        if remaining_days == 0:
            return f"{months} {'month' if months == 1 else 'months'}"
        return f"{months} {'month' if months == 1 else 'months'} and {remaining_days} {'day' if remaining_days == 1 else 'days'}"

def get_pending_prs():
    """
    Fetch pending pull requests from GitHub for specific repository targeting dev-rebirth branch
    Returns a dictionary with repository names as keys and lists of PR details as values
    """
    pending_prs = {}
    
    try:
        # Get the specific repository
        repo = git.get_repo(REPOSITORY)
        
        # Get pull requests that are open and targeting dev-rebirth
        prs = repo.get_pulls(state='open', base=TARGET_BRANCH)
        
        if prs.totalCount > 0:
            pending_prs[repo.name] = []
            for pr in prs:
                created_date = pr.created_at
                days_open = (datetime.now(created_date.tzinfo) - created_date).days
                
                # Get reviewers if any
                reviewers = [review.user.login for review in pr.get_reviews()]
                
                # Check for merge conflicts
                try:
                    mergeable = pr.mergeable
                except GithubException:
                    mergeable = None
                
                # Get checks status 
                checks = pr.get_checks()
                status = 'pending'
                for check in checks:
                    if check.status == 'completed':
                        status = check.conclusion

                pending_prs[repo.name].append({
                    'title': pr.title,
                    'url': pr.html_url,
                    'author': pr.user.login,
                    'created_at': created_date.strftime('%Y-%m-%d'),
                    'days_open': days_open,
                    'duration': format_duration(days_open),
                    'reviewers': reviewers,
                    'pipeline_status': status,
                    'has_conflicts': mergeable is False
                })
            
            # Sort PRs by age (oldest first)
            pending_prs[repo.name].sort(key=lambda x: x['days_open'], reverse=True)
    
    except Exception as e:
        print(f"Error fetching PRs: {e}")
        return {}
    
    return pending_prs

def format_message(pending_prs):
    """
    Format the pending PRs into a readable Slack message
    """
    if not pending_prs:
        return f"No pending pull requests for repository *{REPOSITORY}* on branch *{TARGET_BRANCH}*! 🎉"
    
    message = f"🔍 *Daily Pull Request Reminder*\n"
    message += f"Repository: *{REPOSITORY}*\n"
    message += f"Branch: *{TARGET_BRANCH}*\n\n"
    
    for repo, prs in pending_prs.items():
        for pr in prs:
            message += f"• <{pr['url']}|{pr['title']}>\n"
            message += f"  - Author: {pr['author']}\n"
            message += f"  - Pending for: {pr['duration']}\n"
            
            if pr['reviewers']:
                message += f"  - Reviewers: {', '.join(pr['reviewers'])}\n"
            
            if pr['pipeline_status']:
                status_emoji = {
                    'success': '✅',
                    'failure': '❌',
                    'neutral': '⚪',
                    'cancelled': '❌',
                    'pending': '⏳',
                    'running': '🔄'
                }.get(pr['pipeline_status'], '❔')
                message += f"  - Checks: {status_emoji} {pr['pipeline_status']}\n"
            
            if pr['has_conflicts']:
                message += f"  - ⚠️ *Has merge conflicts!*\n"
            
            # Add warning emoji for PRs pending for more than a week
            if pr['days_open'] >= 7:
                message += f"  ⚠️ *This PR has been pending for over a week!*\n"
            
            message += "\n"
    
    return message

def send_daily_reminder():
    """
    Send daily reminder to specified Slack channel
    """
    if not REPOSITORY:
        print("Error: GITHUB_REPOSITORY environment variable is not set")
        return
        
    pending_prs = get_pending_prs()
    message = format_message(pending_prs)
    
    try:
        # Post message to the specified channel
        app.client.chat_postMessage(
            channel=os.environ["SLACK_CHANNEL_ID"],
            text=message
        )
    except Exception as e:
        print(f"Error sending message: {e}")

def main():
    # Verify required environment variables
    required_vars = [
        'SLACK_BOT_TOKEN',
        'SLACK_APP_TOKEN',
        'SLACK_CHANNEL_ID',
        'GIT_URL',
        'GIT_ACCESS_TOKEN',
        'GIT_PROJECT_PATH',
        'TARGET_BRANCH'
    ]
    
    missing_vars = [var for var in required_vars if not os.environ.get(var)]
    if missing_vars:
        print(f"Error: Missing required environment variables: {', '.join(missing_vars)}")
        return
    
    # Schedule the reminder for every weekday at 10:00 AM
    schedule.every().monday.at("10:00").do(send_daily_reminder)
    schedule.every().tuesday.at("10:00").do(send_daily_reminder)
    schedule.every().wednesday.at("10:00").do(send_daily_reminder)
    schedule.every().thursday.at("10:00").do(send_daily_reminder)
    schedule.every().friday.at("10:00").do(send_daily_reminder)
    
    # Start the Socket Mode handler
    handler = SocketModeHandler(app, os.environ["SLACK_APP_TOKEN"])
    handler.start()
    
    # Keep the script running and execute scheduled tasks
    while True:
        schedule.run_pending()
        time.sleep(60)

if __name__ == "__main__":
    main()