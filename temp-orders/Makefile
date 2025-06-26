# Define variables
GIT_CHECK := $(shell command -v git 2>/dev/null)
CONFTEST_CHECK := $(shell command -v conftest 2>/dev/null)
ACTIONS_REPO_URL := git@github.com:fanatics-gaming/actions.git
BRANCH := main
TEMP_REPO_DIR := ~/.fbg/actions
POLICY_DIR := $(TEMP_REPO_DIR)/opa/product-deploy/policy # Replace with the specific directory

format:
	find . -type f -name "*.rego" -exec dirname {} \; | sort -u | xargs -I{} opa fmt -w {}

test: check-conftest check-git check-access clone-repo
	for cluster_path in deploy/* ; do \
		cluster=$$(basename $$cluster_path); \
		echo "Verifying $$cluster_path"; \
		echo "{\"cluster\": \"$$cluster\"}" > /tmp/data.json; \
		kubectl kustomize "$$cluster_path" | conftest test - --policy ./policy --policy $(POLICY_DIR) --data /tmp/data.json ; \
	done

test-debug: check-conftest check-git check-access clone-repo
	for cluster_path in deploy/* ; do \
		cluster=$$(basename $$cluster_path); \
		echo "Verifying $$cluster_path"; \
		echo "{\"cluster\": \"$$cluster\"}" > /tmp/data.json; \
		kubectl kustomize "$$cluster_path" | conftest test - --policy ./policy --policy $(POLICY_DIR) --trace --data /tmp/data.json ; \
	done

deps:
	brew install opa conftest

# Check if Git is installed
check-git:
ifndef GIT_CHECK
	@echo "Error: Git is not installed."; exit 1
endif
	@echo "Git is installed: $(GIT_CHECK)"

# Check if Conftest is installed
check-conftest:
ifndef CONFTEST_CHECK
	@echo "Error: Conftest is not installed."; exit 1
endif
	@echo "Conftest is installed: $(CONFTEST_CHECK)"

# Check Git access to private repo
check-access:
	@echo "Checking access to the repository..."
	@if git ls-remote $(ACTIONS_REPO_URL) -q &>/dev/null; then \
		echo "Access to the repository is verified."; \
	else \
		echo "Error: Unable to access the repository. Check your SSH keys or permissions."; exit 1; \
	fi

# Clone the repository shallowly if not already cloned
clone-repo:
	@if [ -d $(TEMP_REPO_DIR) ]; then \
		echo "Repository already exists. Running git pull..."; \
		cd $(TEMP_REPO_DIR) && git checkout $(BRANCH) && git pull origin $(BRANCH); \
	else \
		echo "Cloning repository into $(TEMP_REPO_DIR)..."; \
		git clone --depth 1 --branch $(BRANCH) $(ACTIONS_REPO_URL) $(TEMP_REPO_DIR); \
	fi

# Clean up the cloned repo
clean:
	@rm -rf $(TEMP_REPO_DIR)