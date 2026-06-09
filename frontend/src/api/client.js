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
