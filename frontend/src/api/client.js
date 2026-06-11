/**
 * API client for Code Butler backend.
 * All APIs return { code, message, data, timestamp }.
 */

const API_BASE = '/api';

/**
 * Wrapper for fetch that handles JSON parsing and standard response format.
 * @param {string} url - API endpoint path
 * @param {RequestInit} options - fetch options
 * @returns {Promise<any>} parsed data from response
 */
async function request(url, options = {}) {
  const response = await fetch(`${API_BASE}${url}`, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const result = await response.json();

  // 40100 = NOT_LOGIN_ERROR — trigger login modal
  if (result.code === 40100) {
    window.dispatchEvent(new CustomEvent('auth:required'));
    throw new Error(result.message || '请先登录');
  }

  if (result.code !== 0) {
    throw new Error(result.message || 'API request failed');
  }

  return result.data;
}

/**
 * Check backend health status.
 * @returns {Promise<any>}
 */
export function checkHealth() {
  return request('/health');
}

/**
 * Submit code review request.
 * @param {string} repoPath - absolute path to repository
 * @returns {Promise<any>}
 */
export function reviewCode(repoPath) {
  return request(`/code/review?repoPath=${encodeURIComponent(repoPath)}`, {
    method: 'POST',
  });
}

/**
 * Generate documentation.
 * @param {string} repoPath - absolute path to repository
 * @param {string} docType - README / CONTRIBUTING / API
 * @returns {Promise<any>}
 */
export function generateDocs(repoPath, docType) {
  return request(`/code/docs?repoPath=${encodeURIComponent(repoPath)}&docType=${encodeURIComponent(docType)}`, {
    method: 'POST',
  });
}

/**
 * Fetch operation history for the logged-in user.
 * @param {number} page - page number (starts from 1)
 * @param {number} pageSize - items per page
 * @returns {Promise<any>} paginated history records
 */
export function getHistory(page = 1, pageSize = 20) {
  return request(`/code/history?page=${page}&pageSize=${pageSize}`);
}

/**
 * Get current user's review preferences.
 * @returns {Promise<any>} { reviewFocus, reviewDepth, customPrompt }
 */
export function getPreference() {
  return request('/user/preference');
}

/**
 * Update current user's review preferences.
 * @param {{ reviewFocus: string, reviewDepth: string, customPrompt: string }} prefs
 * @returns {Promise<any>}
 */
export function updatePreference(prefs) {
  return request('/user/preference', {
    method: 'PUT',
    body: JSON.stringify(prefs),
  });
}

/**
 * Get available review focus options.
 * @returns {Promise<Record<string,string>>}
 */
export function getFocusOptions() {
  return request('/user/preference/focus-options');
}

/**
 * Get current user's favorite repositories.
 * @returns {Promise<any[]>}
 */
export function getFavoriteRepos() {
  return request('/user/favorite-repos');
}

/**
 * Add a repository to favorites.
 * @param {string} repoPath
 * @param {string} [repoName]
 * @returns {Promise<any>}
 */
export function addFavoriteRepo(repoPath, repoName = '') {
  return request('/user/favorite-repos', {
    method: 'POST',
    body: JSON.stringify({ repoPath, repoName }),
  });
}

/**
 * Remove a repository from favorites.
 * @param {number} id - favorite repo record ID
 * @returns {Promise<any>}
 */
export function removeFavoriteRepo(id) {
  return request(`/user/favorite-repos/${id}`, {
    method: 'DELETE',
  });
}

/**
 * Get current user's usage statistics (today/month calls, tokens, quota).
 * @returns {Promise<any>} UsageStatsVO
 */
export function getUsageStats() {
  return request('/code/usage');
}

/**
 * Get quota configuration (admin only).
 * @returns {Promise<any[]>} list of QuotaConfig
 */
export function getQuotaConfigs() {
  return request('/code/quota/config');
}

/**
 * Update quota configuration (admin only).
 * @param {string} opType - REVIEW / CHAT / DOC
 * @param {number} dailyLimit - daily limit, -1 for unlimited
 * @returns {Promise<any>}
 */
export function updateQuotaConfig(opType, dailyLimit) {
  return request('/code/quota/config', {
    method: 'PUT',
    body: JSON.stringify({ opType, dailyLimit }),
  });
}
