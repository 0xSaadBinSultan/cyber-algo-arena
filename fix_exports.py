import re

with open('public/index.html', 'r') as f:
    html = f.read()

replacement = """    window.showView = showView;
    window.openAuthModal = openAuthModal;
    window.closeAuthModal = closeAuthModal;
    window.logout = logout;
    window.switchChallengeTrack = switchChallengeTrack;
    window.filterCategory = filterCategory;
    window.openChallengeModal = openChallengeModal;
    window.closeModal = closeModal;
    window.switchModalTab = switchModalTab;
    window.filterRadar = filterRadar;
    window.fetchLeaderboard = fetchLeaderboard;
    window.fetchProfile = fetchProfile;
    window.fetchRadarEvents = fetchRadarEvents;"""

html = html.replace("""    window.showView = showView;
    window.openAuthModal = openAuthModal;
    window.closeAuthModal = closeAuthModal;
    window.logout = logout;
    window.switchChallengeTrack = switchChallengeTrack;
    window.filterCategory = filterCategory;
    window.openChallengeModal = openChallengeModal;
    window.closeModal = closeModal;
    window.switchModalTab = switchModalTab;
    window.filterRadar = filterRadar;""", replacement)

with open('public/index.html', 'w') as f:
    f.write(html)
