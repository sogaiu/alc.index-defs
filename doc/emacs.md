# Emacs

## Setup

1. Ensure some appropriate `deps.edn` has been [edited appropriately](../README.md#general-setup-and-use).


2. Index a Clojure code base:

   ```
   $ cd /home/alice/a-clj-proj-dir
   $ clj -A:etags
   ```

3. Wait a bit for indexing to complete -- note that the initial indexing run via `clj`, dependencies may be downloaded so the time-to-wait before `tags` / `TAGS` files are created will likely be longer than otherwise.

## Try it out

1. Launch Emacs and open a Clojure file from the project

2. Put point / cursor on an identifier you want to look up

3. `M-.` -OR- `M-x xref-find-definitions`

4. Likely you'll be asked to specify a location for the TAGS file -- there should be one in the project root now, so specify it.

5. On the happy path, the definition should be in front of you, or there should be a buffer with a list of possible definitions to choose from.

* Check out the top of [Looking Up Identifiers](https://www.gnu.org/software/emacs/manual/html_node/emacs/Looking-Up-Identifiers.html) for other related commands.

### Tips and Troubleshooting

* If you'd prefer not to have to interactively specify the location of `TAGS`, the variable [tags-table-list](https://www.gnu.org/software/emacs/manual/html_node/emacs/Select-Tags-Table.html) might be worth examining.

  Below are a couple of (possibly overly simple) examples:

  ```
  (require 'project) ; probably don't need this

  ;; choose one of the following

  ;; adding to tags-table-list
  (add-hook 'clojure-mode-hook
            (lambda ()
              (when-let ((proj (project-current)))
                (add-to-list 'tags-table-list (cdr proj)))))

  ;; setting tags-table-list
  (add-hook 'clojure-mode-hook
            (lambda ()
              (when-let ((proj (project-current)))
                (setq tags-table-list (cdr proj)))))
  ```

* Rumor has it that `M-x complete-tag` might be able to use the content of `TAGS` to perform completion.

* For reasons unknown at this time, one may get a message / dialog along the lines of:

  ```
  Tags file <path> has changed, read new contents? (yes/no)
  ```

  One work-around is to:

  ```
  (setq tags-revert-without-query t)
  ```

  Thanks to sastanin for [asking](https://stackoverflow.com/questions/4096580/how-to-make-emacs-reload-the-tags-file-automatically) and darioo for [answering](https://stackoverflow.com/a/4097107) at StackOverflow.
